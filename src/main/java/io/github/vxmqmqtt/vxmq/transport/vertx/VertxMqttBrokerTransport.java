package io.github.vxmqmqtt.vxmq.transport.vertx;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.ProtocolEngine;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectDecision;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItem;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.transport.BrokerTransport;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.smallrye.mutiny.Uni;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.mqtt.MqttEndpoint;
import io.vertx.mutiny.mqtt.MqttServer;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Vert.x MQTT transport implementation that bridges network events to the protocol engine.
 */
@ApplicationScoped
public class VertxMqttBrokerTransport implements BrokerTransport {

    private final Vertx vertx;
    private final BrokerRuntimeConfig brokerRuntimeConfig;
    private final ProtocolEngine protocolEngine;
    private final ClientConnectionRegistry connectionRegistry;
    private final BrokerEventSink brokerEventSink;
    private final Map<String, MqttEndpoint> endpointsByConnectionId = new ConcurrentHashMap<>();
    private volatile MqttServer mqttServer;

    public VertxMqttBrokerTransport(
            Vertx vertx,
            BrokerRuntimeConfig brokerRuntimeConfig,
            ProtocolEngine protocolEngine,
            ClientConnectionRegistry connectionRegistry,
            BrokerEventSink brokerEventSink) {
        this.vertx = vertx;
        this.brokerRuntimeConfig = brokerRuntimeConfig;
        this.protocolEngine = protocolEngine;
        this.connectionRegistry = connectionRegistry;
        this.brokerEventSink = brokerEventSink;
    }

    @Override
    public Uni<Void> start() {
        if (mqttServer != null) {
            return Uni.createFrom().voidItem();
        }

        // Transport limits are configured centrally so tests and runtime share the same behavior.
        MqttServerOptions options = new MqttServerOptions()
                .setHost(brokerRuntimeConfig.host())
                .setPort(brokerRuntimeConfig.port())
                .setMaxMessageSize(brokerRuntimeConfig.maxMessageSize())
                .setTimeoutOnConnect(brokerRuntimeConfig.timeoutOnConnectSeconds());

        mqttServer = MqttServer.create(vertx, options);
        mqttServer.endpointHandler(this::handleEndpoint);
        return mqttServer.listen()
                .replaceWithVoid()
                .invoke(() -> brokerEventSink.transportStarted(brokerRuntimeConfig.host(), brokerRuntimeConfig.port()));
    }

    @Override
    public Uni<Void> stop() {
        MqttServer server = mqttServer;
        mqttServer = null;
        if (server == null) {
            return Uni.createFrom().voidItem();
        }

        return server.close().invoke(brokerEventSink::transportStopped);
    }

    private void handleEndpoint(MqttEndpoint endpoint) {
        // The raw Vert.x flag carries MQTT 3.1.1 Clean Session or MQTT 5 Clean Start;
        // ClientConnection stores only the broker-side "start clean" intent.
        ClientConnection connection = connectionRegistry.open(
                endpoint.remoteAddress().toString(),
                endpoint.clientIdentifier(),
                endpoint.protocolName(),
                endpoint.protocolVersion(),
                endpoint.isCleanSession());
        ConnectDecision decision = protocolEngine.handleConnect(connection, buildConnectRequest(endpoint));

        if (!decision.accepted()) {
            endpoint.reject(decision.returnCode(), decision.responseProperties());
            connectionRegistry.close(connection.connectionId());
            return;
        }

        endpoint.setClientIdentifier(decision.effectiveClientId());
        endpoint.accept(decision.sessionPresent(), decision.responseProperties());
        endpointsByConnectionId.put(connection.connectionId(), endpoint);
        installHandlers(connection, endpoint);
        // The transport closes the old socket after the new client id binding is accepted.
        if (decision.supersededConnectionId() != null) {
            closeSupersededConnection(decision.supersededConnectionId());
        }
        protocolEngine.handleSessionResume(connection).forEach(this::sendPublishToSubscriber);
    }

    private void installHandlers(ClientConnection connection, MqttEndpoint endpoint) {
        endpoint.publishAutoAck(false);
        endpoint.publishHandler(message -> {
            PublishResult publishResult = protocolEngine.handlePublish(connection, new PublishRequest(
                    message.topicName(),
                    message.messageId(),
                    message.qosLevel().value(),
                    message.isRetain(),
                    message.isDup(),
                    message.payload() == null ? null : message.payload().getBytes()));

            if (publishResult.closeConnection()) {
                disconnectForInvalidPublish(connection, endpoint, publishResult.disconnectReasonCode());
                return;
            }

            if (!publishResult.accepted()) {
                return;
            }

            if (publishResult.publishAcknowledge()) {
                acknowledgeInboundPublish(endpoint, message.messageId(), connection.protocolVersion(), publishResult);
            }

            // Delivery fan-out stays in the transport because it needs access to live endpoints.
            publishResult.deliveries().forEach(this::sendPublishToSubscriber);
        });

        endpoint.subscribeHandler(subscribe -> {
            SubscribeResult subscribeResult = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(
                    subscribe.topicSubscriptions()
                            .stream()
                            .map(subscription -> new SubscriptionItem(
                                    subscription.topicName(),
                                    subscription.qualityOfService().value()))
                            .collect(Collectors.toList())));

            if (connection.protocolVersion() == 5) {
                endpoint.subscribeAcknowledge(
                        subscribe.messageId(),
                        subscribeResult.reasonCodes(),
                        MqttProperties.NO_PROPERTIES);
            } else {
                endpoint.subscribeAcknowledge(subscribe.messageId(), subscribeResult.grantedQosLevels());
            }
        });

        endpoint.unsubscribeHandler(unsubscribe -> {
            UnsubscribeResult unsubscribeResult =
                    protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(unsubscribe.topics()));
            if (connection.protocolVersion() == 5) {
                endpoint.unsubscribeAcknowledge(
                        unsubscribe.messageId(),
                        unsubscribeResult.reasonCodes(),
                        MqttProperties.NO_PROPERTIES);
            } else {
                endpoint.unsubscribeAcknowledge(unsubscribe.messageId());
            }
        });

        endpoint.disconnectHandler(() -> protocolEngine.handleDisconnect(connection));
        endpoint.publishAcknowledgeHandler(packetId -> protocolEngine.handlePubAck(connection, packetId));
        endpoint.closeHandler(() -> {
            protocolEngine.handleConnectionClosed(connection);
            endpointsByConnectionId.remove(connection.connectionId());
            connectionRegistry.close(connection.connectionId());
        });
    }

    private ConnectRequest buildConnectRequest(MqttEndpoint endpoint) {
        if (endpoint.protocolVersion() == 4) {
            return ConnectRequest.mqtt311(
                    endpoint.clientIdentifier(),
                    endpoint.protocolName(),
                    endpoint.isCleanSession(),
                    username(endpoint.auth()),
                    passwordPresent(endpoint.auth()));
        }
        if (endpoint.protocolVersion() == 5) {
            return ConnectRequest.mqtt5(
                    endpoint.clientIdentifier(),
                    endpoint.protocolName(),
                    endpoint.isCleanSession(),
                    sessionExpiryIntervalSeconds(endpoint.connectProperties()),
                    username(endpoint.auth()),
                    passwordPresent(endpoint.auth()));
        }

        return new ConnectRequest(
                endpoint.clientIdentifier(),
                endpoint.protocolName(),
                endpoint.protocolVersion(),
                null,
                null,
                null,
                username(endpoint.auth()),
                passwordPresent(endpoint.auth()));
    }

    private void closeSupersededConnection(String connectionId) {
        MqttEndpoint supersededEndpoint = endpointsByConnectionId.remove(connectionId);
        if (supersededEndpoint != null && supersededEndpoint.isConnected()) {
            closeEndpointWithMqtt5Reason(supersededEndpoint, MqttDisconnectReasonCode.SESSION_TAKEN_OVER);
        }
    }

    private void sendPublishToSubscriber(PublishDelivery delivery) {
        // Re-resolve the active connection to avoid publishing to a client that has been taken over.
        connectionRegistry.findActiveConnectionId(delivery.clientId())
                .map(endpointsByConnectionId::get)
                .ifPresent(endpoint ->
                        outboundPublish(endpoint, delivery)
                                .subscribe()
                                .with(
                                        ignored -> {
                                        },
                                        failure -> brokerEventSink.protocolWarning(null,
                                                "Failed to publish to subscriber clientId=%s: %s"
                                                        .formatted(delivery.clientId(), failure.getMessage()))));
    }

    private void disconnectForInvalidPublish(
            ClientConnection connection,
            MqttEndpoint endpoint,
            MqttDisconnectReasonCode reasonCode) {
        // MQTT 5 can signal a precise reason code, while older versions fall back to closing the socket.
        if (connection.protocolVersion() == 5 && reasonCode != null) {
            endpoint.disconnect(reasonCode, MqttProperties.NO_PROPERTIES);
            return;
        }
        endpoint.close();
    }

    private void acknowledgeInboundPublish(
            MqttEndpoint endpoint,
            int packetId,
            int protocolVersion,
            PublishResult publishResult) {
        if (protocolVersion == 5) {
            endpoint.publishAcknowledge(packetId, publishResult.pubAckReasonCode(), MqttProperties.NO_PROPERTIES);
            return;
        }
        endpoint.publishAcknowledge(packetId);
    }

    private Uni<Integer> outboundPublish(MqttEndpoint endpoint, PublishDelivery delivery) {
        Buffer payload = delivery.payload() == null ? Buffer.buffer() : Buffer.buffer(delivery.payloadCopy());
        if (delivery.grantedQos() == io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE && delivery.packetId() != null) {
            return endpoint.publish(
                    delivery.topicName(),
                    payload,
                    delivery.grantedQos(),
                    delivery.duplicate(),
                    delivery.retain(),
                    delivery.packetId());
        }
        return endpoint.publish(
                delivery.topicName(),
                payload,
                delivery.grantedQos(),
                delivery.duplicate(),
                delivery.retain());
    }

    /**
     * Sends a MQTT 5 DISCONNECT when the protocol supports it, otherwise just closes the socket.
     */
    static void closeEndpointWithMqtt5Reason(MqttEndpoint endpoint, MqttDisconnectReasonCode reasonCode) {
        if (endpoint.protocolVersion() == 5 && reasonCode != null) {
            endpoint.disconnect(reasonCode, MqttProperties.NO_PROPERTIES);
            return;
        }
        endpoint.close();
    }

    private String username(MqttAuth auth) {
        return auth == null ? null : auth.getUsername();
    }

    private boolean passwordPresent(MqttAuth auth) {
        return auth != null && auth.getPassword() != null;
    }

    private long sessionExpiryIntervalSeconds(MqttProperties connectProperties) {
        if (connectProperties == null || connectProperties.isEmpty()) {
            return 0L;
        }

        MqttProperties.MqttProperty<?> property = connectProperties.getProperty(
                MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
        if (property == null || property.value() == null) {
            return 0L;
        }
        return ((Number) property.value()).longValue();
    }

    /**
     * Exposes the actual listening port for integration tests that use an ephemeral port.
     */
    int actualPort() {
        MqttServer server = mqttServer;
        return server == null ? -1 : server.actualPort();
    }
}
