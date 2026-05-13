package io.github.vxmqmqtt.vxmq.transport.vertx;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.ProtocolEngine;
import io.github.vxmqmqtt.vxmq.protocol.model.AcceptedConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPubRelOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPublishOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt311ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttPacketSizeEstimator;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttUserProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.OutboundPubRecOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgement;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgementType;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishReleaseDisposition;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttUserProperty;
import io.github.vxmqmqtt.vxmq.protocol.model.RejectedConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.ReplayPubRel;
import io.github.vxmqmqtt.vxmq.protocol.model.ReplayPublish;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumeAction;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumePlan;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItem;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeAck;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsupportedConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.transport.BrokerTransport;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.smallrye.mutiny.Uni;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttWill;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.mqtt.MqttEndpoint;
import io.vertx.mutiny.mqtt.MqttServer;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    // key: connectionId, value: MqttEndpoint
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
        ConnectOutcome decision = protocolEngine.handleConnect(connection, buildConnectRequest(endpoint));
        ConnectResponse response = decision.response();
        if (response instanceof RejectedConnectResponse(
                MqttConnectReturnCode returnCode, MqttProperties responseProperties
        )) {
            endpoint.reject(returnCode, responseProperties);
            connectionRegistry.close(connection.connectionId());
            return;
        }

        AcceptedConnectResponse acceptedResponse = (AcceptedConnectResponse) response;
        endpoint.setClientIdentifier(acceptedResponse.effectiveClientId());
        endpoint.accept(acceptedResponse.sessionPresent(), acceptedResponse.responseProperties());
        endpointsByConnectionId.put(connection.connectionId(), endpoint);
        installHandlers(connection, endpoint);
        // The transport closes the old socket after the new client id binding is accepted.
        if (decision.takeoverPlan().requiresTakeover()) {
            closeSupersededConnection(decision.takeoverPlan().supersededConnectionId());
        }
        sendSessionResume(protocolEngine.handleSessionResume(connection), endpoint);
    }

    private void installHandlers(ClientConnection connection, MqttEndpoint endpoint) {
        endpoint.publishAutoAck(false);

        endpoint.publishHandler(message -> {
            InboundPublishOutcome publishOutcome = protocolEngine.handlePublish(connection, new PublishRequest(
                    message.topicName(),
                    message.messageId(),
                    message.qosLevel().value(),
                    message.isRetain(),
                    message.isDup(),
                    message.payload() == null ? null : message.payload().getBytes(),
                    publishProperties(connection.protocolVersion(), message.properties()),
                    MqttPacketSizeEstimator.publishPacketSize(
                            message.topicName(),
                            message.payload() == null ? 0 : message.payload().length(),
                            message.qosLevel().value(),
                            publishPropertiesSize(connection.protocolVersion(), message.properties()))));

            if (publishOutcome.disconnectAction().isDisconnect()) {
                disconnectForInvalidPublish(connection, endpoint, publishOutcome.disconnectAction().reasonCode());
                return;
            }

            sendInboundPublishAcknowledgement(
                    endpoint,
                    message.messageId(),
                    connection.protocolVersion(),
                    publishOutcome.acknowledgement());

            // Delivery fan-out stays in the transport because it needs access to live endpoints.
            publishOutcome.deliveryPlan().deliveries().forEach(this::sendPublishToSubscriber);
        });

        endpoint.subscribeHandler(subscribe -> {
            SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(
                    subscribe.topicSubscriptions()
                            .stream()
                            .map(subscription -> new SubscriptionItem(
                                    subscription.topicName(),
                                    subscription.qualityOfService().value(),
                                    subscriptionOption(subscription).isNoLocal(),
                                    subscriptionOption(subscription).isRetainAsPublished(),
                                    subscriptionOption(subscription).retainHandling(),
                                    null))
                            .collect(Collectors.toList()),
                    new SubscriptionProperties(
                            userProperties(connection.protocolVersion(), subscribe.properties()),
                            subscriptionIdentifier(connection.protocolVersion(), subscribe.properties()))));

            if (subscribeResult.disconnectAction().isDisconnect()) {
                disconnectForInvalidPublish(connection, endpoint, subscribeResult.disconnectAction().reasonCode());
                return;
            }

            if (connection.protocolVersion() == 5) {
                endpoint.subscribeAcknowledge(
                        subscribe.messageId(),
                        subscribeResult.ack().reasonCodes(),
                        MqttProperties.NO_PROPERTIES);
            } else {
                endpoint.subscribeAcknowledge(subscribe.messageId(), subscribeResult.ack().grantedQosLevels());
            }

            subscribeResult.retainedReplayPlan().deliveries().forEach(this::sendPublishToSubscriber);
        });

        endpoint.unsubscribeHandler(unsubscribe -> {
            UnsubscribeAck unsubscribeResult =
                    protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(
                            unsubscribe.topics(),
                            userProperties(connection.protocolVersion(), unsubscribe.properties())));
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

        endpoint.publishAcknowledgeHandler(packetId ->
                protocolEngine.handlePubAck(connection, packetId)
                        .deliveries()
                        .forEach(this::sendPublishToSubscriber));

        endpoint.publishReleaseHandler(packetId -> {
            InboundPubRelOutcome pubRelResult = protocolEngine.handlePubRel(connection, packetId);
            pubRelResult.deliveryPlan().deliveries().forEach(this::sendPublishToSubscriber);
            completeInboundPublish(endpoint, packetId, connection.protocolVersion(), pubRelResult);
        });

        endpoint.publishReceivedHandler(packetId -> {
            OutboundPubRecOutcome pubRecResult = protocolEngine.handlePubRec(connection, packetId);
            if (pubRecResult.disposition() == PublishReleaseDisposition.SEND) {
                releaseOutboundPublish(endpoint, packetId, connection.protocolVersion(), pubRecResult.reasonCode());
            }
        });

        endpoint.publishCompletionHandler(packetId ->
                protocolEngine.handlePubComp(connection, packetId)
                        .deliveries()
                        .forEach(this::sendPublishToSubscriber));

        endpoint.closeHandler(() -> {
            endpointsByConnectionId.remove(connection.connectionId());
            connectionRegistry.close(connection.connectionId());
            protocolEngine.handleConnectionClosed(connection).forEach(this::sendPublishToSubscriber);
        });
    }

    private ConnectRequest buildConnectRequest(MqttEndpoint endpoint) {
        if (endpoint.protocolVersion() == 4) {
            return new Mqtt311ConnectRequest(
                    endpoint.clientIdentifier(),
                    endpoint.protocolName(),
                    endpoint.isCleanSession(),
                    username(endpoint.auth()),
                    password(endpoint.auth()),
                    passwordPresent(endpoint.auth()),
                    willMessage(endpoint.protocolVersion(), endpoint.will()));
        }
        if (endpoint.protocolVersion() == 5) {
            return new Mqtt5ConnectRequest(
                    endpoint.clientIdentifier(),
                    endpoint.protocolName(),
                    endpoint.isCleanSession(),
                    sessionExpiryIntervalSeconds(endpoint.connectProperties()),
                    username(endpoint.auth()),
                    password(endpoint.auth()),
                    passwordPresent(endpoint.auth()),
                    willMessage(endpoint.protocolVersion(), endpoint.will()),
                    new ConnectProperties(
                            userProperties(endpoint.protocolVersion(), endpoint.connectProperties()),
                            receiveMaximum(endpoint.connectProperties()),
                            maximumPacketSize(endpoint.connectProperties())));
        }

        return new UnsupportedConnectRequest(
                endpoint.clientIdentifier(),
                endpoint.protocolName(),
                endpoint.protocolVersion(),
                username(endpoint.auth()),
                password(endpoint.auth()),
                passwordPresent(endpoint.auth()),
                willMessage(endpoint.protocolVersion(), endpoint.will()));
    }

    private void closeSupersededConnection(String connectionId) {
        MqttEndpoint supersededEndpoint = endpointsByConnectionId.remove(connectionId);
        if (supersededEndpoint != null && supersededEndpoint.isConnected()) {
            closeEndpointWithMqtt5Reason(supersededEndpoint, MqttDisconnectReasonCode.SESSION_TAKEN_OVER);
        }
    }

    private void sendPublishToSubscriber(PublishDelivery delivery) {
        // Re-resolve the active connection to avoid publishing to a client that has been taken over.
        String activeConnectionId = connectionRegistry.findActiveConnectionId(delivery.clientId()).orElse(null);
        if (activeConnectionId == null) {
            brokerEventSink.protocolWarning(null,
                    "Skipped publish to subscriber clientId=%s: no active connection"
                            .formatted(delivery.clientId()));
            return;
        }

        MqttEndpoint endpoint = endpointsByConnectionId.get(activeConnectionId);
        if (endpoint == null) {
            brokerEventSink.protocolWarning(null,
                    "Skipped publish to subscriber clientId=%s connectionId=%s: endpoint is not registered"
                            .formatted(delivery.clientId(), activeConnectionId));
            return;
        }
        if (!endpoint.isConnected()) {
            brokerEventSink.protocolWarning(null,
                    "Skipped publish to subscriber clientId=%s connectionId=%s: endpoint is not connected"
                            .formatted(delivery.clientId(), activeConnectionId));
            return;
        }

        outboundPublish(endpoint, delivery)
                .subscribe()
                .with(
                        ignored -> {
                        },
                        failure -> brokerEventSink.protocolWarning(null,
                                "Failed to publish to subscriber clientId=%s connectionId=%s: %s"
                                        .formatted(delivery.clientId(), activeConnectionId, failure.getMessage())));
    }

    private void sendSessionResume(SessionResumePlan resumePlan, MqttEndpoint endpoint) {
        for (SessionResumeAction action : resumePlan.actions()) {
            if (action instanceof ReplayPublish(PublishDelivery delivery)) {
                sendPublishToSubscriber(delivery);
                continue;
            }
            if (action instanceof ReplayPubRel(int packetId)) {
                releaseOutboundPublish(
                        endpoint,
                        packetId,
                        endpoint.protocolVersion(),
                        MqttPubRelReasonCode.SUCCESS);
            }
        }
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

    private void sendInboundPublishAcknowledgement(
            MqttEndpoint endpoint,
            int packetId,
            int protocolVersion,
            PublishAcknowledgement acknowledgement) {
        if (acknowledgement.type() == PublishAcknowledgementType.NONE) {
            return;
        }

        if (acknowledgement.type() == PublishAcknowledgementType.PUBACK) {
            if (protocolVersion == 5) {
                endpoint.publishAcknowledge(
                        packetId,
                        (MqttPubAckReasonCode) acknowledgement.mqtt5ReasonCode(),
                        MqttProperties.NO_PROPERTIES);
                return;
            }
            endpoint.publishAcknowledge(packetId);
            return;
        }

        if (protocolVersion == 5) {
            endpoint.publishReceived(
                    packetId,
                    (MqttPubRecReasonCode) acknowledgement.mqtt5ReasonCode(),
                    MqttProperties.NO_PROPERTIES);
            return;
        }
        endpoint.publishReceived(packetId);
    }

    private void completeInboundPublish(
            MqttEndpoint endpoint,
            int packetId,
            int protocolVersion,
            InboundPubRelOutcome pubRelResult) {
        if (protocolVersion == 5) {
            endpoint.publishComplete(packetId, pubRelResult.completionReasonCode(), MqttProperties.NO_PROPERTIES);
            return;
        }
        endpoint.publishComplete(packetId);
    }

    private void releaseOutboundPublish(
            MqttEndpoint endpoint,
            int packetId,
            int protocolVersion,
            MqttPubRelReasonCode reasonCode) {
        if (protocolVersion == 5) {
            endpoint.publishRelease(packetId, reasonCode, MqttProperties.NO_PROPERTIES);
            return;
        }
        endpoint.publishRelease(packetId);
    }

    private Uni<Integer> outboundPublish(MqttEndpoint endpoint, PublishDelivery delivery) {
        Buffer payload = delivery.payload() == null ? Buffer.buffer() : Buffer.buffer(delivery.payloadCopy());
        MqttProperties publishProperties = publishProperties(endpoint.protocolVersion(), delivery);
        if (endpoint.protocolVersion() == 5
                && (!delivery.subscriptionIdentifiers().isEmpty() || !delivery.properties().isEmpty())) {
            return endpoint.publish(
                    delivery.topicName(),
                    payload,
                    delivery.grantedQos(),
                    delivery.duplicate(),
                    delivery.retain(),
                    delivery.packetId() == null ? 0 : delivery.packetId(),
                    publishProperties);
        }
        if (delivery.grantedQos().value() > 0 && delivery.packetId() != null) {
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

    private MqttProperties publishProperties(int protocolVersion, PublishDelivery delivery) {
        if (protocolVersion != 5 || (delivery.subscriptionIdentifiers().isEmpty() && delivery.properties().isEmpty())) {
            return MqttProperties.NO_PROPERTIES;
        }
        MqttProperties properties = new MqttProperties();
        for (MqttUserProperty userProperty : delivery.properties().userProperties().values()) {
            properties.add(new MqttProperties.UserProperty(userProperty.key(), userProperty.value()));
        }
        delivery.properties()
                .messageExpiry()
                .remainingIntervalSeconds(Instant.now())
                .ifPresent(remaining -> properties.add(new MqttProperties.IntegerProperty(
                        MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value(),
                        (int) remaining)));
        if (delivery.properties().responseTopic() != null) {
            properties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value(),
                    delivery.properties().responseTopic()));
        }
        if (delivery.properties().correlationData() != null) {
            properties.add(new MqttProperties.BinaryProperty(
                    MqttProperties.MqttPropertyType.CORRELATION_DATA.value(),
                    delivery.properties().correlationData()));
        }
        if (delivery.properties().payloadFormatIndicator() != null) {
            properties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value(),
                    delivery.properties().payloadFormatIndicator()));
        }
        if (delivery.properties().contentType() != null) {
            properties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.CONTENT_TYPE.value(),
                    delivery.properties().contentType()));
        }
        for (Integer subscriptionIdentifier : delivery.subscriptionIdentifiers()) {
            properties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value(),
                    subscriptionIdentifier));
        }
        return properties;
    }

    private PublishProperties publishProperties(int protocolVersion, MqttProperties mqttProperties) {
        return new PublishProperties(
                userProperties(protocolVersion, mqttProperties),
                messageExpiry(protocolVersion, mqttProperties),
                responseTopic(protocolVersion, mqttProperties),
                correlationData(protocolVersion, mqttProperties),
                payloadFormatIndicator(protocolVersion, mqttProperties),
                contentType(protocolVersion, mqttProperties));
    }

    private int publishPropertiesSize(int protocolVersion, MqttProperties mqttProperties) {
        return MqttPacketSizeEstimator.publishPropertiesSize(publishProperties(protocolVersion, mqttProperties));
    }

    private MqttUserProperties userProperties(int protocolVersion, MqttProperties mqttProperties) {
        if (protocolVersion != 5 || mqttProperties == null) {
            return MqttUserProperties.empty();
        }
        List<MqttUserProperty> userProperties = new ArrayList<>();
        for (MqttProperties.MqttProperty<?> property : mqttProperties.getProperties(
                MqttProperties.MqttPropertyType.USER_PROPERTY.value())) {
            MqttProperties.StringPair pair = (MqttProperties.StringPair) property.value();
            userProperties.add(new MqttUserProperty(pair.key, pair.value));
        }
        return userProperties.isEmpty() ? MqttUserProperties.empty() : new MqttUserProperties(userProperties);
    }

    private io.github.vxmqmqtt.vxmq.protocol.model.MessageExpiry messageExpiry(
            int protocolVersion,
            MqttProperties mqttProperties) {
        if (protocolVersion != 5 || mqttProperties == null) {
            return io.github.vxmqmqtt.vxmq.protocol.model.MessageExpiry.none();
        }
        MqttProperties.MqttProperty<?> property = mqttProperties.getProperty(
                MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value());
        if (property == null || property.value() == null) {
            return io.github.vxmqmqtt.vxmq.protocol.model.MessageExpiry.none();
        }
        long intervalSeconds = Integer.toUnsignedLong(((Number) property.value()).intValue());
        return io.github.vxmqmqtt.vxmq.protocol.model.MessageExpiry.fromIntervalSeconds(intervalSeconds, Instant.now());
    }

    private String responseTopic(int protocolVersion, MqttProperties mqttProperties) {
        if (protocolVersion != 5 || mqttProperties == null) {
            return null;
        }
        MqttProperties.MqttProperty<?> property = mqttProperties.getProperty(
                MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value());
        if (property == null || property.value() == null) {
            return null;
        }
        return (String) property.value();
    }

    private byte[] correlationData(int protocolVersion, MqttProperties mqttProperties) {
        if (protocolVersion != 5 || mqttProperties == null) {
            return null;
        }
        MqttProperties.MqttProperty<?> property = mqttProperties.getProperty(
                MqttProperties.MqttPropertyType.CORRELATION_DATA.value());
        if (property == null || property.value() == null) {
            return null;
        }
        byte[] value = (byte[]) property.value();
        return value.clone();
    }

    private Integer payloadFormatIndicator(int protocolVersion, MqttProperties mqttProperties) {
        if (protocolVersion != 5 || mqttProperties == null) {
            return null;
        }
        MqttProperties.MqttProperty<?> property = mqttProperties.getProperty(
                MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value());
        if (property == null || property.value() == null) {
            return null;
        }
        return ((Number) property.value()).intValue();
    }

    private String contentType(int protocolVersion, MqttProperties mqttProperties) {
        if (protocolVersion != 5 || mqttProperties == null) {
            return null;
        }
        MqttProperties.MqttProperty<?> property = mqttProperties.getProperty(
                MqttProperties.MqttPropertyType.CONTENT_TYPE.value());
        if (property == null || property.value() == null) {
            return null;
        }
        return (String) property.value();
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

    private String password(MqttAuth auth) {
        return auth == null ? null : auth.getPassword();
    }

    private boolean passwordPresent(MqttAuth auth) {
        return auth != null && auth.getPassword() != null;
    }

    private Long sessionExpiryIntervalSeconds(MqttProperties connectProperties) {
        if (connectProperties == null || connectProperties.isEmpty()) {
            return null;
        }

        MqttProperties.MqttProperty<?> property = connectProperties.getProperty(
                MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
        if (property == null || property.value() == null) {
            return null;
        }
        return Integer.toUnsignedLong(((Number) property.value()).intValue());
    }

    private Integer receiveMaximum(MqttProperties connectProperties) {
        if (connectProperties == null || connectProperties.isEmpty()) {
            return null;
        }
        MqttProperties.MqttProperty<?> property = connectProperties.getProperty(
                MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM.value());
        if (property == null || property.value() == null) {
            return null;
        }
        int value = ((Number) property.value()).intValue();
        return value;
    }

    private Integer maximumPacketSize(MqttProperties connectProperties) {
        if (connectProperties == null || connectProperties.isEmpty()) {
            return null;
        }
        MqttProperties.MqttProperty<?> property = connectProperties.getProperty(
                MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE.value());
        if (property == null || property.value() == null) {
            return null;
        }
        int value = ((Number) property.value()).intValue();
        return value;
    }

    private MqttSubscriptionOption subscriptionOption(io.vertx.mutiny.mqtt.MqttTopicSubscription subscription) {
        MqttSubscriptionOption option = subscription.subscriptionOption();
        return option == null
                ? MqttSubscriptionOption.onlyFromQos(subscription.qualityOfService())
                : option;
    }

    private Integer subscriptionIdentifier(int protocolVersion, MqttProperties subscribeProperties) {
        if (protocolVersion != 5 || subscribeProperties == null) {
            return null;
        }
        List<? extends MqttProperties.MqttProperty> properties = subscribeProperties.getProperties(
                MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value());
        if (properties.isEmpty()) {
            return null;
        }
        MqttProperties.MqttProperty<?> property = properties.getFirst();
        if (property == null || property.value() == null) {
            return null;
        }
        return ((Number) property.value()).intValue();
    }

    private WillMessage willMessage(int protocolVersion, MqttWill will) {
        if (will == null || !will.isWillFlag()) {
            return null;
        }
        return new WillMessage(
                will.getWillTopic(),
                will.getWillMessageBytes() == null ? null : will.getWillMessageBytes().clone(),
                switch (will.getWillQos()) {
                    case 2 -> io.netty.handler.codec.mqtt.MqttQoS.EXACTLY_ONCE;
                    case 1 -> io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
                    default -> io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;
                },
                will.isWillRetain(),
                willPublishProperties(protocolVersion, will.getWillProperties()));
    }

    private PublishProperties willPublishProperties(int protocolVersion, MqttProperties mqttProperties) {
        return new PublishProperties(
                userProperties(protocolVersion, mqttProperties),
                io.github.vxmqmqtt.vxmq.protocol.model.MessageExpiry.none(),
                responseTopic(protocolVersion, mqttProperties),
                correlationData(protocolVersion, mqttProperties),
                payloadFormatIndicator(protocolVersion, mqttProperties),
                contentType(protocolVersion, mqttProperties));
    }

    /**
     * Exposes the actual listening port for integration tests that use an ephemeral port.
     */
    int actualPort() {
        MqttServer server = mqttServer;
        return server == null ? -1 : server.actualPort();
    }
}
