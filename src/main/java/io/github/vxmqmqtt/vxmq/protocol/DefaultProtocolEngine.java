package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.auth.AuthProvider;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.model.AcceptedConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectionTakeoverPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.DeliveryPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPubRelOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPublishOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt311ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.OutboundPubRecOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgement;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.RejectedConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.ReplayPubRel;
import io.github.vxmqmqtt.vxmq.protocol.model.ReplayPublish;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumeAction;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumePlan;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeAck;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItem;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItemResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeAck;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeItemResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.protocol.model.RetainedReplayPlan;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessage;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import io.github.vxmqmqtt.vxmq.session.ClientSession;
import io.github.vxmqmqtt.vxmq.session.InboundQos2Message;
import io.github.vxmqmqtt.vxmq.session.InflightMessage;
import io.github.vxmqmqtt.vxmq.session.OutboundQos2State;
import io.github.vxmqmqtt.vxmq.session.QueuedMessage;
import io.github.vxmqmqtt.vxmq.session.SessionOpenRequest;
import io.github.vxmqmqtt.vxmq.session.SessionOpenResult;
import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ConnectionState;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default in-memory protocol engine for the current single-node milestone.
 */
@ApplicationScoped
public class DefaultProtocolEngine implements ProtocolEngine {

    private final AuthProvider authProvider;
    private final SessionRegistry sessionRegistry;
    private final RetainedMessageRegistry retainedMessageRegistry;
    private final SubscriptionRegistry subscriptionRegistry;
    private final MqttTopicSupport mqttTopicSupport;
    private final BrokerEventSink brokerEventSink;
    private final ClientConnectionRegistry connectionRegistry;

    public DefaultProtocolEngine(
            AuthProvider authProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry) {
        this.authProvider = authProvider;
        this.sessionRegistry = sessionRegistry;
        this.retainedMessageRegistry = retainedMessageRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.mqttTopicSupport = mqttTopicSupport;
        this.brokerEventSink = brokerEventSink;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public ConnectOutcome handleConnect(ClientConnection connection, ConnectRequest request) {
        // Reject unsupported protocol names or versions before any state is mutated.
        if (!"MQTT".equals(request.protocolName()) || (!request.isMqtt311() && !request.isMqtt5())) {
            brokerEventSink.protocolWarning(connection, "Unsupported protocol version: " + request.protocolVersion());
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    rejectUnsupportedProtocolVersion(request),
                    MqttProperties.NO_PROPERTIES));
        }

        if (!authProvider.allowConnect(connection, request)) {
            brokerEventSink.protocolWarning(connection, "Connection rejected by auth provider");
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    rejectNotAuthorized(request),
                    MqttProperties.NO_PROPERTIES));
        }

        String effectiveClientId = resolveClientId(request);
        if (effectiveClientId == null) {
            brokerEventSink.protocolWarning(connection, "Client identifier rejected");
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    rejectInvalidClientId(request),
                    MqttProperties.NO_PROPERTIES));
        }

        MqttProperties responseProperties = buildConnectResponseProperties(request, effectiveClientId);
        SessionOpenResult sessionOpenResult = sessionRegistry.openSession(
                effectiveClientId,
                buildSessionOpenRequest(request, connection.connectionId()));
        clearRoutingBindings(sessionOpenResult.clearedSession());
        connection.assignClientId(effectiveClientId);
        connection.assignWillMessage(request.willMessage());
        connection.transitionTo(ConnectionState.CONNECTED);
        // A new connection with the same client identifier replaces the old one.
        String supersededConnectionId = connectionRegistry.bindClientId(effectiveClientId, connection.connectionId())
                .orElse(null);
        brokerEventSink.connectionAccepted(connection);
        return ConnectOutcome.accepted(
                new AcceptedConnectResponse(
                        sessionOpenResult.sessionPresent(),
                        effectiveClientId,
                        responseProperties),
                supersededConnectionId == null
                        ? ConnectionTakeoverPlan.none()
                        : ConnectionTakeoverPlan.takeOver(supersededConnectionId));
    }

    @Override
    public SubscribeOutcome handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
        List<SubscriptionItemResult> results = new ArrayList<>();
        List<PublishDelivery> retainedDeliveries = new ArrayList<>();
        for (SubscriptionItem item : request.items()) {
            String topicFilter = item.topicFilter();
            if (!mqttTopicSupport.isValidFilter(topicFilter)) {
                brokerEventSink.protocolWarning(connection, "Rejected invalid topic filter: " + topicFilter);
                results.add(SubscriptionItemResult.rejected(topicFilter, MqttSubAckReasonCode.TOPIC_FILTER_INVALID));
                continue;
            }

            if (!isSupportedRequestedQos(item.requestedQos())) {
                brokerEventSink.protocolWarning(connection, "Rejected unsupported requested QoS: " + item.requestedQos());
                results.add(SubscriptionItemResult.rejected(topicFilter, MqttSubAckReasonCode.IMPLEMENTATION_SPECIFIC_ERROR));
                continue;
            }

            MqttQoS grantedQos = grantedSubscriptionQos(item.requestedQos());
            SubscriptionBinding subscriptionBinding = new SubscriptionBinding(
                    connection.effectiveClientId(),
                    topicFilter,
                    grantedQos,
                    item.noLocal(),
                    item.retainAsPublished(),
                    item.retainHandling(),
                    item.subscriptionIdentifier());
            boolean subscriptionAlreadyExisted = sessionRegistry.find(connection.effectiveClientId())
                    .map(session -> session.subscription(topicFilter) != null)
                    .orElse(false);
            try {
                sessionRegistry.addSubscription(subscriptionBinding);
                subscriptionRegistry.addSubscription(subscriptionBinding);
                brokerEventSink.subscriptionAdded(connection, topicFilter);
                results.add(SubscriptionItemResult.granted(topicFilter, grantedQos));
                if (shouldReplayRetained(item.retainHandling(), subscriptionAlreadyExisted)) {
                    retainedDeliveries.addAll(buildRetainedDeliveries(subscriptionBinding));
                }
            } catch (RuntimeException exception) {
                // Roll back the session view if the routing registry write fails.
                sessionRegistry.removeSubscription(connection.effectiveClientId(), topicFilter);
                brokerEventSink.protocolWarning(connection, "Failed to register subscription: " + topicFilter);
                results.add(SubscriptionItemResult.rejected(topicFilter, MqttSubAckReasonCode.UNSPECIFIED_ERROR));
            }
        }
        return new SubscribeOutcome(
                new SubscribeAck(results),
                retainedDeliveries.isEmpty()
                        ? RetainedReplayPlan.empty()
                        : new RetainedReplayPlan(retainedDeliveries));
    }

    @Override
    public UnsubscribeAck handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request) {
        List<UnsubscribeItemResult> results = new ArrayList<>();
        for (String topicFilter : request.topicFilters()) {
            if (!mqttTopicSupport.isValidFilter(topicFilter)) {
                brokerEventSink.protocolWarning(connection, "Rejected invalid topic filter for unsubscribe: " + topicFilter);
                results.add(UnsubscribeItemResult.rejected(topicFilter, MqttUnsubAckReasonCode.TOPIC_FILTER_INVALID));
                continue;
            }

            try {
                // Both registries are cleaned up so MQTT 5 can report whether anything existed.
                boolean removedFromSession = sessionRegistry.removeSubscription(connection.effectiveClientId(), topicFilter);
                boolean removedFromRouting = subscriptionRegistry.removeSubscription(connection.effectiveClientId(), topicFilter);
                if (removedFromSession || removedFromRouting) {
                    brokerEventSink.subscriptionRemoved(connection, topicFilter);
                    results.add(UnsubscribeItemResult.success(topicFilter));
                } else {
                    results.add(UnsubscribeItemResult.noSubscriptionExisted(topicFilter));
                }
            } catch (RuntimeException exception) {
                brokerEventSink.protocolWarning(connection, "Failed to remove subscription: " + topicFilter);
                results.add(UnsubscribeItemResult.rejected(topicFilter, MqttUnsubAckReasonCode.UNSPECIFIED_ERROR));
            }
        }
        return new UnsubscribeAck(results);
    }

    @Override
    public InboundPublishOutcome handlePublish(ClientConnection connection, PublishRequest request) {
        if (!mqttTopicSupport.isValidTopicName(request.topicName())) {
            brokerEventSink.protocolWarning(connection, "Rejected publish with invalid topic name: " + request.topicName());
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
        }

        if (request.qos() < 0 || request.qos() > 2) {
            brokerEventSink.protocolWarning(connection, "Rejected unsupported inbound QoS: " + request.qos());
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED);
        }

        if (request.qos() == 2) {
            if (connection.effectiveClientId() == null || request.packetId() <= 0) {
                brokerEventSink.protocolWarning(connection, "Rejected QoS 2 publish without a packet id");
                return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.PROTOCOL_ERROR);
            }
            sessionRegistry.startInboundQos2Message(
                    connection.effectiveClientId(),
                    request.packetId(),
                    request.topicName(),
                    request.payload(),
                    request.retain(),
                    request.duplicate());
            return InboundPublishOutcome.deferred(PublishAcknowledgement.pubRec(MqttPubRecReasonCode.SUCCESS));
        }

        PublishRoutingResult routingResult = routePublish(connection, request);
        PublishAcknowledgement acknowledgement = request.qos() == 1
                ? PublishAcknowledgement.pubAck(MqttPubAckReasonCode.SUCCESS)
                : PublishAcknowledgement.none();
        return InboundPublishOutcome.completed(
                DeliveryPlan.of(routingResult.deliveries(), routingResult.queuedMessageCount()),
                acknowledgement);
    }

    private PublishRoutingResult routePublish(ClientConnection connection, PublishRequest request) {
        updateRetainedMessageIfRequested(request);

        List<PublishDelivery> deliveries = new ArrayList<>();
        int queuedMessageCount = 0;
        for (SubscriptionBinding binding : subscriptionRegistry.match(request.topicName())) {
            if (binding.noLocal() && binding.clientId().equals(connection.effectiveClientId())) {
                continue;
            }
            MqttQoS deliveryQos = grantedDeliveryQos(request.qos(), binding.grantedQos());
            boolean deliveryRetain = binding.retainAsPublished() && request.retain();
            boolean online = connectionRegistry.findActiveConnectionId(binding.clientId()).isPresent();
            if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
                if (online) {
                    deliveries.add(new PublishDelivery(
                            binding.clientId(),
                            request.topicName(),
                            copyPayload(request.payload()),
                            MqttQoS.AT_MOST_ONCE,
                            deliveryRetain,
                            false,
                            null,
                            false,
                            binding.subscriptionIdentifiers()));
                }
                continue;
            }

            if (online) {
                sessionRegistry.createInflightMessage(
                                binding.clientId(),
                                request.topicName(),
                                request.payload(),
                                deliveryQos,
                                deliveryRetain,
                                false,
                                false,
                                binding.subscriptionIdentifiers())
                        .map(inflightMessage -> toPublishDelivery(binding.clientId(), inflightMessage))
                        .ifPresent(deliveries::add);
                continue;
            }

            ClientSession session = sessionRegistry.find(binding.clientId()).orElse(null);
            if (session != null && session.persistent()) {
                sessionRegistry.enqueueOfflineMessage(binding.clientId(), new QueuedMessage(
                        request.topicName(),
                        copyPayload(request.payload()),
                        deliveryQos,
                        deliveryRetain,
                        false,
                        binding.subscriptionIdentifiers()));
                queuedMessageCount++;
            }
        }

        int matchedClients = deliveries.size() + queuedMessageCount;
        brokerEventSink.messageRouted(connection, request.topicName(), matchedClients);
        return new PublishRoutingResult(deliveries, queuedMessageCount);
    }

    @Override
    public SessionResumePlan handleSessionResume(ClientConnection connection) {
        if (connection.effectiveClientId() == null) {
            return SessionResumePlan.empty();
        }

        ClientSession session = sessionRegistry.find(connection.effectiveClientId()).orElse(null);
        if (session == null || !connection.connectionId().equals(session.connectionId())) {
            return SessionResumePlan.empty();
        }

        List<InflightMessage> drainedMessages = sessionRegistry.drainQueuedMessages(connection.effectiveClientId());
        List<Integer> drainedPacketIds = drainedMessages.stream()
                .map(InflightMessage::packetId)
                .toList();
        List<SessionResumeAction> actions = new ArrayList<>(drainedMessages.stream()
                .map(inflightMessage -> new ReplayPublish(toPublishDelivery(connection.effectiveClientId(), inflightMessage)))
                .toList());
        for (InflightMessage inflightMessage : sessionRegistry.outboundQos2InflightMessages(connection.effectiveClientId())) {
            if (drainedPacketIds.contains(inflightMessage.packetId())) {
                continue;
            }
            if (inflightMessage.qos2State() == OutboundQos2State.PUBREL_SENT) {
                actions.add(new ReplayPubRel(inflightMessage.packetId()));
            } else {
                actions.add(new ReplayPublish(toPublishDelivery(connection.effectiveClientId(), inflightMessage)));
            }
        }
        return actions.isEmpty() ? SessionResumePlan.empty() : new SessionResumePlan(actions);
    }

    @Override
    public void handlePubAck(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.acknowledge(connection.effectiveClientId(), packetId);
        }
    }

    @Override
    public InboundPubRelOutcome handlePubRel(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() == null) {
            return InboundPubRelOutcome.alreadyComplete();
        }

        InboundQos2Message inboundMessage = sessionRegistry.completeInboundQos2Message(
                        connection.effectiveClientId(),
                        packetId)
                .orElse(null);
        if (inboundMessage == null) {
            return InboundPubRelOutcome.alreadyComplete();
        }

        PublishRoutingResult routingResult = routePublish(connection, new PublishRequest(
                inboundMessage.topicName(),
                inboundMessage.packetId(),
                2,
                inboundMessage.retain(),
                inboundMessage.duplicate(),
                inboundMessage.payloadCopy()));
        return InboundPubRelOutcome.completed(
                DeliveryPlan.of(routingResult.deliveries(), routingResult.queuedMessageCount()));
    }

    @Override
    public OutboundPubRecOutcome handlePubRec(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() == null) {
            return OutboundPubRecOutcome.skip(MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND);
        }

        return sessionRegistry.markOutboundQos2PubRec(connection.effectiveClientId(), packetId)
                .map(ignored -> OutboundPubRecOutcome.send(MqttPubRelReasonCode.SUCCESS))
                .orElseGet(() -> OutboundPubRecOutcome.skip(MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND));
    }

    @Override
    public void handlePubComp(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.completeOutboundQos2(connection.effectiveClientId(), packetId);
        }
    }

    @Override
    public void handleDisconnect(ClientConnection connection) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.discardWillMessage(connection.effectiveClientId(), connection.connectionId());
        }
        connection.clearWillMessage();
        connection.transitionTo(ConnectionState.DISCONNECTING);
    }

    @Override
    public List<PublishDelivery> handleConnectionClosed(ClientConnection connection) {
        List<PublishDelivery> willDeliveries = List.of();
        if (shouldPublishWill(connection)) {
            willDeliveries = publishWill(connection);
        }
        if (connection.effectiveClientId() != null) {
            clearRoutingBindings(sessionRegistry.onConnectionClosed(connection.effectiveClientId(), connection.connectionId())
                    .orElse(null));
        }
        connection.transitionTo(ConnectionState.CLOSED);
        return willDeliveries;
    }

    private SessionOpenRequest buildSessionOpenRequest(ConnectRequest request, String connectionId) {
        // MQTT 3.1.1 and MQTT 5 share the same open/restore flow, but differ in persistence semantics.
        Long sessionExpiryIntervalSeconds = request instanceof Mqtt5ConnectRequest mqtt5Request
                ? mqtt5Request.sessionExpiryIntervalSeconds()
                : null;
        return new SessionOpenRequest(
                startsFreshSession(request),
                retainsSessionOnDisconnect(request),
                sessionExpiryIntervalSeconds,
                connectionId,
                request.willMessage());
    }

    private String resolveClientId(ConnectRequest request) {
        // Explicit client identifiers always win over auto-assignment.
        if (request.requestedClientId() != null && !request.requestedClientId().isBlank()) {
            return request.requestedClientId();
        }

        // MQTT 3.1.1 requires a persistent session to carry a non-empty client identifier.
        if (request instanceof Mqtt311ConnectRequest mqtt311Request && !mqtt311Request.cleanSession()) {
            return null;
        }

        if (request.isMqtt311() || request.isMqtt5()) {
            return generateClientId();
        }

        return null;
    }

    private String generateClientId() {
        return "vxmq-" + UUID.randomUUID();
    }

    private MqttProperties buildConnectResponseProperties(ConnectRequest request, String effectiveClientId) {
        // Assigned Client Identifier is only required for MQTT 5 auto-generated client ids.
        if (!request.isMqtt5() || (request.requestedClientId() != null && !request.requestedClientId().isBlank())) {
            return MqttProperties.NO_PROPERTIES;
        }

        MqttProperties properties = new MqttProperties();
        properties.add(new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value(),
                effectiveClientId));
        return properties;
    }

    private MqttConnectReturnCode rejectUnsupportedProtocolVersion(ConnectRequest request) {
        if (request.protocolVersion() >= 5) {
            return MqttConnectReturnCode.CONNECTION_REFUSED_UNSUPPORTED_PROTOCOL_VERSION;
        }
        return MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION;
    }

    private MqttConnectReturnCode rejectNotAuthorized(ConnectRequest request) {
        if (request.isMqtt5()) {
            return MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5;
        }
        return MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
    }

    private MqttConnectReturnCode rejectInvalidClientId(ConnectRequest request) {
        if (request.isMqtt5()) {
            return MqttConnectReturnCode.CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID;
        }
        return MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
    }

    private boolean isSupportedRequestedQos(int requestedQos) {
        return requestedQos >= 0 && requestedQos <= 2;
    }

    private boolean startsFreshSession(ConnectRequest request) {
        if (request instanceof Mqtt311ConnectRequest mqtt311Request) {
            return mqtt311Request.cleanSession();
        }
        if (request instanceof Mqtt5ConnectRequest mqtt5Request) {
            return mqtt5Request.cleanStart();
        }
        return true;
    }

    private boolean retainsSessionOnDisconnect(ConnectRequest request) {
        if (request instanceof Mqtt311ConnectRequest mqtt311Request) {
            return !mqtt311Request.cleanSession();
        }
        if (request instanceof Mqtt5ConnectRequest mqtt5Request) {
            return mqtt5Request.sessionExpiryIntervalSeconds() > 0;
        }
        return false;
    }

    private MqttQoS grantedSubscriptionQos(int requestedQos) {
        if (requestedQos <= 0) {
            return MqttQoS.AT_MOST_ONCE;
        }
        if (requestedQos == 2) {
            return MqttQoS.EXACTLY_ONCE;
        }
        return MqttQoS.AT_LEAST_ONCE;
    }

    private MqttQoS grantedDeliveryQos(int publishQos, MqttQoS subscriptionQos) {
        int value = Math.min(publishQos, subscriptionQos.value());
        if (value <= 0) {
            return MqttQoS.AT_MOST_ONCE;
        }
        return value == 1 ? MqttQoS.AT_LEAST_ONCE : MqttQoS.EXACTLY_ONCE;
    }

    private void updateRetainedMessageIfRequested(PublishRequest request) {
        if (!request.retain()) {
            return;
        }

        if (request.payloadSize() == 0) {
            retainedMessageRegistry.removeRetained(request.topicName());
            return;
        }

        retainedMessageRegistry.putRetained(
                request.topicName(),
                request.payload(),
                grantedDeliveryQos(request.qos(), MqttQoS.EXACTLY_ONCE));
    }

    private boolean shouldReplayRetained(RetainedHandlingPolicy retainHandling, boolean subscriptionAlreadyExisted) {
        return switch (retainHandling) {
            case SEND_AT_SUBSCRIBE -> true;
            case SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS -> !subscriptionAlreadyExisted;
            case DONT_SEND_AT_SUBSCRIBE -> false;
        };
    }

    private List<PublishDelivery> buildRetainedDeliveries(SubscriptionBinding subscriptionBinding) {
        List<PublishDelivery> deliveries = new ArrayList<>();
        for (RetainedMessage retainedMessage : retainedMessageRegistry.findMatching(subscriptionBinding.topicFilter())) {
            MqttQoS deliveryQos = grantedDeliveryQos(retainedMessage.qos().value(), subscriptionBinding.grantedQos());
            if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
                deliveries.add(new PublishDelivery(
                        subscriptionBinding.clientId(),
                        retainedMessage.topicName(),
                        retainedMessage.payloadCopy(),
                        MqttQoS.AT_MOST_ONCE,
                        true,
                        false,
                        null,
                        false,
                        subscriptionBinding.subscriptionIdentifiers()));
                continue;
            }

            sessionRegistry.createInflightMessage(
                            subscriptionBinding.clientId(),
                            retainedMessage.topicName(),
                            retainedMessage.payloadCopy(),
                            deliveryQos,
                            true,
                            false,
                            false,
                            subscriptionBinding.subscriptionIdentifiers())
                    .map(inflightMessage -> toPublishDelivery(subscriptionBinding.clientId(), inflightMessage))
                    .ifPresent(deliveries::add);
        }
        return deliveries;
    }

    private PublishDelivery toPublishDelivery(String clientId, InflightMessage inflightMessage) {
        return new PublishDelivery(
                clientId,
                inflightMessage.topicName(),
                copyPayload(inflightMessage.payload()),
                inflightMessage.qos(),
                inflightMessage.retain(),
                inflightMessage.duplicate(),
                inflightMessage.packetId(),
                inflightMessage.fromOfflineQueue(),
                inflightMessage.subscriptionIdentifiers());
    }

    private byte[] copyPayload(byte[] payload) {
        return payload == null ? null : payload.clone();
    }

    private record PublishRoutingResult(List<PublishDelivery> deliveries, int queuedMessageCount) {
    }

    private void clearRoutingBindings(ClientSession clearedSession) {
        if (clearedSession == null) {
            return;
        }

        for (String topicFilter : clearedSession.subscriptions()) {
            subscriptionRegistry.removeSubscription(clearedSession.clientId(), topicFilter);
        }
    }

    private boolean shouldPublishWill(ClientConnection connection) {
        return connection.effectiveClientId() != null && connection.state() != ConnectionState.DISCONNECTING;
    }

    private List<PublishDelivery> publishWill(ClientConnection connection) {
        WillMessage willMessage = connection.takeWillMessage();
        if (willMessage == null) {
            return List.of();
        }

        if (connection.effectiveClientId() != null) {
            sessionRegistry.discardWillMessage(connection.effectiveClientId(), connection.connectionId());
        }

        InboundPublishOutcome publishResult = handlePublish(connection, new PublishRequest(
                willMessage.topicName(),
                0,
                willMessage.qos().value(),
                willMessage.retain(),
                false,
                willMessage.payloadCopy()));
        return publishResult.deliveryPlan().deliveries();
    }
}
