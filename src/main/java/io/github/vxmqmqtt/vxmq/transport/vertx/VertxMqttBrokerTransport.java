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
import io.netty.handler.codec.mqtt.MqttQoS;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        ClientConnection connection = connectionRegistry.open(
                endpoint.remoteAddress().toString(),
                endpoint.clientIdentifier(),
                endpoint.protocolName(),
                endpoint.protocolVersion(),
                endpoint.isCleanSession());
        ConnectDecision decision = protocolEngine.handleConnect(connection, new ConnectRequest(
                endpoint.clientIdentifier(),
                endpoint.protocolName(),
                endpoint.protocolVersion(),
                endpoint.isCleanSession(),
                username(endpoint.auth()),
                passwordPresent(endpoint.auth())));

        if (!decision.accepted()) {
            endpoint.reject(decision.returnCode(), decision.responseProperties());
            connectionRegistry.close(connection.internalId());
            return;
        }

        endpoint.setClientIdentifier(decision.effectiveClientId());
        endpoint.accept(decision.sessionPresent(), decision.responseProperties());
        endpointsByConnectionId.put(connection.internalId(), endpoint);
        if (decision.supersededConnectionId() != null) {
            closeSupersededConnection(decision.supersededConnectionId());
        }
        installHandlers(connection, endpoint);
    }

    private void installHandlers(ClientConnection connection, MqttEndpoint endpoint) {
        endpoint.publishHandler(message -> {
            PublishResult publishResult = protocolEngine.handlePublish(connection, new PublishRequest(
                    message.topicName(),
                    message.qosLevel().value(),
                    message.isRetain(),
                    message.isDup(),
                    message.payload() == null ? null : message.payload().getBytes()));

            if (!publishResult.accepted()) {
                disconnectForInvalidPublish(connection, endpoint, publishResult.disconnectReasonCode());
                return;
            }

            publishResult.deliveries()
                    .forEach(delivery -> sendPublishToSubscriber(delivery, message.topicName(), message.payload(), message.isRetain()));
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
        endpoint.closeHandler(() -> {
            protocolEngine.handleConnectionClosed(connection);
            endpointsByConnectionId.remove(connection.internalId());
            connectionRegistry.close(connection.internalId());
        });
    }

    private void closeSupersededConnection(String connectionId) {
        MqttEndpoint supersededEndpoint = endpointsByConnectionId.remove(connectionId);
        if (supersededEndpoint != null && supersededEndpoint.isConnected()) {
            supersededEndpoint.close();
        }
    }

    private void sendPublishToSubscriber(
            PublishDelivery delivery,
            String topicName,
            Buffer payload,
            boolean retain) {
        connectionRegistry.findActiveConnectionId(delivery.clientId())
                .map(endpointsByConnectionId::get)
                .ifPresent(endpoint ->
                        endpoint.publish(topicName, payload == null ? Buffer.buffer() : payload, delivery.grantedQos(), false, retain)
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
        if (connection.protocolVersion() == 5 && reasonCode != null) {
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

    int actualPort() {
        MqttServer server = mqttServer;
        return server == null ? -1 : server.actualPort();
    }
}
