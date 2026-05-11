package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.authn.AuthnProvider;
import io.github.vxmqmqtt.vxmq.authn.AuthnResult;
import io.github.vxmqmqtt.vxmq.authz.AuthzAction;
import io.github.vxmqmqtt.vxmq.authz.AuthzChain;
import io.github.vxmqmqtt.vxmq.authz.AuthzContext;
import io.github.vxmqmqtt.vxmq.authz.AuthzProvider;
import io.github.vxmqmqtt.vxmq.authz.AuthzResult;
import io.github.vxmqmqtt.vxmq.authz.ConfiguredAuthzProvider;
import io.github.vxmqmqtt.vxmq.authz.PermitAllAuthzProvider;
import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.model.AcceptedConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectionTakeoverPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.DeliveryPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.DisconnectAction;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPubRelOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPublishOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt311ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttPacketSizeEstimator;
import io.github.vxmqmqtt.vxmq.protocol.model.OutboundPubRecOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgement;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
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
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionProperties;
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
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default in-memory protocol engine for the current single-node milestone.
 */
@ApplicationScoped
public class DefaultProtocolEngine implements ProtocolEngine {

    private final AuthnProvider authnProvider;
    private final AuthzProvider authzProvider;
    private final SessionRegistry sessionRegistry;
    private final RetainedMessageRegistry retainedMessageRegistry;
    private final SubscriptionRegistry subscriptionRegistry;
    private final MqttTopicSupport mqttTopicSupport;
    private final BrokerEventSink brokerEventSink;
    private final ClientConnectionRegistry connectionRegistry;
    private final Clock clock;
    private final int brokerReceiveMaximum;
    private final int brokerMaximumPacketSize;

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry) {
        this(
                authnProvider,
                new PermitAllAuthzProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                Clock.systemUTC());
    }

    @Inject
    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            AuthzProvider authzProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            BrokerRuntimeConfig brokerRuntimeConfig) {
        this(
                authnProvider,
                authzProvider,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                Clock.systemUTC(),
                receiveMaximum(brokerRuntimeConfig),
                maximumPacketSize(brokerRuntimeConfig));
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock) {
        this(
                authnProvider,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                65_535,
                268_435_455);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock,
            int brokerReceiveMaximum) {
        this(
                authnProvider,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                brokerReceiveMaximum,
                268_435_455);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock,
            int brokerReceiveMaximum,
            int brokerMaximumPacketSize) {
        this(
                authnProvider,
                new PermitAllAuthzProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                brokerReceiveMaximum,
                brokerMaximumPacketSize);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            AuthzChain authzChain,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock) {
        this(
                authnProvider,
                authzChain,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                65_535,
                268_435_455);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            AuthzChain authzChain,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock,
            int brokerReceiveMaximum) {
        this(
                authnProvider,
                authzChain,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                brokerReceiveMaximum,
                268_435_455);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            AuthzChain authzChain,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock,
            int brokerReceiveMaximum,
            int brokerMaximumPacketSize) {
        this(
                authnProvider,
                authzChain == null
                        ? new PermitAllAuthzProvider()
                        : new ConfiguredAuthzProvider(authzChain),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                brokerReceiveMaximum,
                brokerMaximumPacketSize);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            AuthzProvider authzProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock) {
        this(
                authnProvider,
                authzProvider,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                65_535,
                268_435_455);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            AuthzProvider authzProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock,
            int brokerReceiveMaximum) {
        this(
                authnProvider,
                authzProvider,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                brokerEventSink,
                connectionRegistry,
                clock,
                brokerReceiveMaximum,
                268_435_455);
    }

    public DefaultProtocolEngine(
            AuthnProvider authnProvider,
            AuthzProvider authzProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry,
            Clock clock,
            int brokerReceiveMaximum,
            int brokerMaximumPacketSize) {
        this.authnProvider = authnProvider;
        this.authzProvider = authzProvider == null
                ? new PermitAllAuthzProvider()
                : authzProvider;
        this.sessionRegistry = sessionRegistry;
        this.retainedMessageRegistry = retainedMessageRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.mqttTopicSupport = mqttTopicSupport;
        this.brokerEventSink = brokerEventSink;
        this.connectionRegistry = connectionRegistry;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.brokerReceiveMaximum = validateReceiveMaximum(brokerReceiveMaximum);
        this.brokerMaximumPacketSize = validateMaximumPacketSize(brokerMaximumPacketSize);
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
        if (hasInvalidConnectProperties(request)) {
            brokerEventSink.protocolWarning(connection, "Invalid MQTT 5 CONNECT properties");
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR,
                    MqttProperties.NO_PROPERTIES));
        }

        AuthnResult authnResult = authnProvider.authenticate(connection, request);
        if (!authnResult.allowed()) {
            brokerEventSink.protocolWarning(
                    connection,
                    "Connection rejected by authn provider: "
                            + authnResult.reason()
                            + diagnosticSuffix(authnResult));
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

        AuthzResult willAuthzResult = authorizeWillPublish(
                connection,
                effectiveClientId,
                authnResult.principal(),
                request);
        if (!willAuthzResult.allowed()) {
            brokerEventSink.protocolWarning(
                    connection,
                    "Connection rejected by will authorization: "
                            + willAuthzResult.reason()
                            + diagnosticSuffix(willAuthzResult));
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    rejectNotAuthorized(request),
                    MqttProperties.NO_PROPERTIES));
        }

        MqttProperties responseProperties = buildConnectResponseProperties(request, effectiveClientId);
        SessionOpenResult sessionOpenResult = sessionRegistry.openSession(
                effectiveClientId,
                buildSessionOpenRequest(request, connection.connectionId()));
        clearRoutingBindings(sessionOpenResult.clearedSession());
        connection.assignClientId(effectiveClientId);
        connection.assignPrincipal(authnResult.principal());
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
        if (hasInvalidSubscriptionProperties(request.properties())) {
            brokerEventSink.protocolWarning(connection, "Invalid MQTT 5 SUBSCRIBE properties");
            return new SubscribeOutcome(
                    new SubscribeAck(List.of()),
                    RetainedReplayPlan.empty(),
                    DisconnectAction.disconnect(MqttDisconnectReasonCode.PROTOCOL_ERROR));
        }

        List<SubscriptionItemResult> results = new ArrayList<>();
        List<PublishDelivery> retainedDeliveries = new ArrayList<>();
        Integer subscriptionIdentifier = request.properties().subscriptionIdentifier();
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

            AuthzResult authzResult = authzProvider.authorize(new AuthzContext(
                    connection,
                    connection.effectiveClientId(),
                    connection.principal(),
                    AuthzAction.SUBSCRIBE,
                    topicFilter));
            if (!authzResult.allowed()) {
                brokerEventSink.protocolWarning(
                        connection,
                        "Rejected unauthorized subscription: "
                                + topicFilter
                                + " reason="
                                + authzResult.reason()
                                + diagnosticSuffix(authzResult));
                results.add(SubscriptionItemResult.rejected(topicFilter, MqttSubAckReasonCode.NOT_AUTHORIZED));
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
                    subscriptionIdentifier == null ? item.subscriptionIdentifier() : subscriptionIdentifier);
            SubscriptionBinding previousSubscription = sessionRegistry.find(connection.effectiveClientId())
                    .map(session -> session.subscription(topicFilter))
                    .orElse(null);
            boolean subscriptionAlreadyExisted = previousSubscription != null;
            try {
                sessionRegistry.addSubscription(subscriptionBinding);
                subscriptionRegistry.addSubscription(subscriptionBinding);
                brokerEventSink.subscriptionAdded(connection, topicFilter);
                results.add(SubscriptionItemResult.granted(topicFilter, grantedQos));
                if (shouldReplayRetained(item.retainHandling(), subscriptionAlreadyExisted)) {
                    retainedDeliveries.addAll(buildRetainedDeliveries(subscriptionBinding));
                }
            } catch (RuntimeException exception) {
                restoreSessionSubscription(connection.effectiveClientId(), topicFilter, previousSubscription);
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

            SubscriptionBinding previousSubscription = sessionRegistry.find(connection.effectiveClientId())
                    .map(session -> session.subscription(topicFilter))
                    .orElse(null);
            boolean removedFromSession = false;
            try {
                // Both registries are cleaned up so MQTT 5 can report whether anything existed.
                removedFromSession = sessionRegistry.removeSubscription(connection.effectiveClientId(), topicFilter);
                boolean removedFromRouting = subscriptionRegistry.removeSubscription(connection.effectiveClientId(), topicFilter);
                if (removedFromSession || removedFromRouting) {
                    brokerEventSink.subscriptionRemoved(connection, topicFilter);
                    results.add(UnsubscribeItemResult.success(topicFilter));
                } else {
                    results.add(UnsubscribeItemResult.noSubscriptionExisted(topicFilter));
                }
            } catch (RuntimeException exception) {
                if (removedFromSession && previousSubscription != null) {
                    sessionRegistry.addSubscription(previousSubscription);
                }
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

        if (connection.protocolVersion() == 5 && request.packetSize() > brokerMaximumPacketSize) {
            brokerEventSink.protocolWarning(connection, "Rejected publish over maximum packet size");
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.PACKET_TOO_LARGE);
        }

        AuthzResult authzResult = authzProvider.authorize(new AuthzContext(
                connection,
                connection.effectiveClientId(),
                connection.principal(),
                AuthzAction.PUBLISH,
                request.topicName()));
        if (!authzResult.allowed()) {
            brokerEventSink.protocolWarning(
                    connection,
                    "Rejected unauthorized publish: "
                            + request.topicName()
                            + " reason="
                            + authzResult.reason()
                            + diagnosticSuffix(authzResult));
            return rejectUnauthorizedPublish(connection, request);
        }

        if (request.qos() == 2) {
            if (connection.effectiveClientId() == null || request.packetId() <= 0) {
                brokerEventSink.protocolWarning(connection, "Rejected QoS 2 publish without a packet id");
                return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.PROTOCOL_ERROR);
            }
            if (sessionRegistry.find(connection.effectiveClientId())
                    .map(ClientSession::inboundQos2MessageCount)
                    .orElse(0) >= brokerReceiveMaximum
                    && !sessionRegistry.hasInboundQos2Message(connection.effectiveClientId(), request.packetId())) {
                brokerEventSink.protocolWarning(connection, "Rejected QoS 2 publish over receive maximum");
                return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.RECEIVE_MAXIMUM_EXCEEDED);
            }
            sessionRegistry.startInboundQos2Message(
                    connection.effectiveClientId(),
                    request.packetId(),
                    request.topicName(),
                    request.payload(),
                    request.retain(),
                    request.duplicate(),
                    request.properties());
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
        Instant now = clock.instant();
        updateRetainedMessageIfRequested(request, now);
        if (request.properties().messageExpiry().isExpired(now)) {
            brokerEventSink.messageRouted(connection, request.topicName(), 0);
            return new PublishRoutingResult(List.of(), 0);
        }

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
                if (online && canSendPublish(binding.clientId(), request.topicName(), request.payload(), deliveryQos,
                        deliveryRetain, false, request.properties(), binding.subscriptionIdentifiers())) {
                    deliveries.add(new PublishDelivery(
                            binding.clientId(),
                            request.topicName(),
                            copyPayload(request.payload()),
                            MqttQoS.AT_MOST_ONCE,
                            deliveryRetain,
                            false,
                            null,
                            false,
                            request.properties(),
                            binding.subscriptionIdentifiers()));
                }
                continue;
            }

            if (online) {
                if (!canSendPublish(binding.clientId(), request.topicName(), request.payload(), deliveryQos,
                        deliveryRetain, false, request.properties(), binding.subscriptionIdentifiers())) {
                    continue;
                }
                InflightMessage inflightMessage = sessionRegistry.createInflightMessage(
                                binding.clientId(),
                                request.topicName(),
                                request.payload(),
                                deliveryQos,
                                deliveryRetain,
                                false,
                                false,
                                request.properties(),
                                binding.subscriptionIdentifiers())
                        .orElse(null);
                if (inflightMessage == null) {
                    sessionRegistry.enqueuePendingOutboundMessage(binding.clientId(), new QueuedMessage(
                            request.topicName(),
                            copyPayload(request.payload()),
                            deliveryQos,
                            deliveryRetain,
                            false,
                            request.properties(),
                            binding.subscriptionIdentifiers()));
                } else {
                    deliveries.add(toPublishDelivery(binding.clientId(), inflightMessage));
                }
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
                        request.properties(),
                        binding.subscriptionIdentifiers()));
                queuedMessageCount++;
            }
        }

        int matchedClients = deliveries.size() + queuedMessageCount;
        brokerEventSink.messageRouted(connection, request.topicName(), matchedClients);
        return new PublishRoutingResult(deliveries, queuedMessageCount);
    }

     private PublishRoutingResult routeServerPublish(ClientConnection connection, PublishRequest request) {
        if (!mqttTopicSupport.isValidTopicName(request.topicName())) {
            brokerEventSink.protocolWarning(connection, "Rejected server publish with invalid topic name: " + request.topicName());
            return new PublishRoutingResult(List.of(), 0);
        }
        return routePublish(connection, request);
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

        Instant now = clock.instant();
        List<InflightMessage> drainedMessages = drainQueuedMessagesForResume(connection.effectiveClientId(), now);
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
            } else if (inflightMessage.properties().messageExpiry().isExpired(now)) {
                sessionRegistry.completeOutboundQos2(connection.effectiveClientId(), inflightMessage.packetId());
            } else if (!canSendPublish(connection.effectiveClientId(), inflightMessage)) {
                clearOutboundInflight(connection.effectiveClientId(), inflightMessage);
            } else {
                actions.add(new ReplayPublish(toPublishDelivery(connection.effectiveClientId(), inflightMessage)));
            }
        }
        drainPendingInflight(connection.effectiveClientId(), now)
                .stream()
                .map(inflightMessage -> new ReplayPublish(toPublishDelivery(connection.effectiveClientId(), inflightMessage)))
                .forEach(actions::add);
        return actions.isEmpty() ? SessionResumePlan.empty() : new SessionResumePlan(actions);
    }

    @Override
    public DeliveryPlan handlePubAck(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.acknowledge(connection.effectiveClientId(), packetId);
            return drainPendingDeliveries(connection.effectiveClientId());
        }
        return DeliveryPlan.empty();
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
                inboundMessage.payloadCopy(),
                inboundMessage.properties()));
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
    public DeliveryPlan handlePubComp(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.completeOutboundQos2(connection.effectiveClientId(), packetId);
            return drainPendingDeliveries(connection.effectiveClientId());
        }
        return DeliveryPlan.empty();
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
                ? effectiveSessionExpiryIntervalSeconds(mqtt5Request)
                : null;
        return new SessionOpenRequest(
                startsFreshSession(request),
                retainsSessionOnDisconnect(request),
                sessionExpiryIntervalSeconds,
                connectionId,
                request.willMessage(),
                effectiveReceiveMaximum(request.properties()),
                effectiveMaximumPacketSize(request.properties()));
    }

    private static int effectiveReceiveMaximum(ConnectProperties properties) {
        Integer receiveMaximum = properties.receiveMaximum();
        return receiveMaximum == null ? ConnectProperties.DEFAULT_RECEIVE_MAXIMUM : receiveMaximum;
    }

    private static int effectiveMaximumPacketSize(ConnectProperties properties) {
        Integer maximumPacketSize = properties.maximumPacketSize();
        return maximumPacketSize == null ? ConnectProperties.DEFAULT_MAXIMUM_PACKET_SIZE : maximumPacketSize;
    }

    private static long effectiveSessionExpiryIntervalSeconds(Mqtt5ConnectRequest request) {
        Long sessionExpiryIntervalSeconds = request.sessionExpiryIntervalSeconds();
        return sessionExpiryIntervalSeconds == null ? 0L : sessionExpiryIntervalSeconds;
    }

    private static boolean hasInvalidConnectProperties(ConnectRequest request) {
        if (!request.isMqtt5()) {
            return false;
        }
        ConnectProperties properties = request.properties();
        return isInvalidReceiveMaximum(properties.receiveMaximum())
                || isInvalidMaximumPacketSize(properties.maximumPacketSize());
    }

    private static boolean isInvalidReceiveMaximum(Integer receiveMaximum) {
        return receiveMaximum != null
                && (receiveMaximum < 1 || receiveMaximum > ConnectProperties.DEFAULT_RECEIVE_MAXIMUM);
    }

    private static boolean isInvalidMaximumPacketSize(Integer maximumPacketSize) {
        return maximumPacketSize != null
                && (maximumPacketSize < 1 || maximumPacketSize > ConnectProperties.DEFAULT_MAXIMUM_PACKET_SIZE);
    }

    private static boolean hasInvalidSubscriptionProperties(SubscriptionProperties properties) {
        Integer subscriptionIdentifier = properties.subscriptionIdentifier();
        return properties.duplicateSubscriptionIdentifier()
                || (subscriptionIdentifier != null && subscriptionIdentifier < 1);
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
        if (!request.isMqtt5()) {
            return MqttProperties.NO_PROPERTIES;
        }

        MqttProperties properties = new MqttProperties();
        if (request.requestedClientId() == null || request.requestedClientId().isBlank()) {
            properties.add(new MqttProperties.StringProperty(
                    MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value(),
                    effectiveClientId));
        }
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM.value(),
                brokerReceiveMaximum));
        properties.add(new MqttProperties.IntegerProperty(
                MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE.value(),
                brokerMaximumPacketSize));
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

    private AuthzResult authorizeWillPublish(
            ClientConnection connection,
            String effectiveClientId,
            String principal,
            ConnectRequest request) {
        WillMessage willMessage = request.willMessage();
        if (willMessage == null) {
            return AuthzResult.allow();
        }
        return authzProvider.authorize(new AuthzContext(
                connection,
                effectiveClientId,
                principal,
                AuthzAction.PUBLISH,
                willMessage.topicName()));
    }

    private InboundPublishOutcome rejectUnauthorizedPublish(ClientConnection connection, PublishRequest request) {
        if (connection.protocolVersion() == 5) {
            if (request.qos() == 1) {
                return InboundPublishOutcome.completed(
                        DeliveryPlan.empty(),
                        PublishAcknowledgement.pubAck(MqttPubAckReasonCode.NOT_AUTHORIZED));
            }
            if (request.qos() == 2) {
                return InboundPublishOutcome.deferred(
                        PublishAcknowledgement.pubRec(MqttPubRecReasonCode.NOT_AUTHORIZED));
            }
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.NOT_AUTHORIZED);
        }
        return InboundPublishOutcome.rejectedWithDisconnect(null);
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
            Long sessionExpiryIntervalSeconds = mqtt5Request.sessionExpiryIntervalSeconds();
            return sessionExpiryIntervalSeconds != null && sessionExpiryIntervalSeconds > 0;
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

    private void updateRetainedMessageIfRequested(PublishRequest request, Instant now) {
        if (!request.retain()) {
            return;
        }

        if (request.payloadSize() == 0) {
            retainedMessageRegistry.removeRetained(request.topicName());
            return;
        }

        if (request.properties().messageExpiry().isExpired(now)) {
            retainedMessageRegistry.removeRetained(request.topicName());
            return;
        }

        retainedMessageRegistry.putRetained(
                request.topicName(),
                request.payload(),
                grantedDeliveryQos(request.qos(), MqttQoS.EXACTLY_ONCE),
                request.properties());
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
        Instant now = clock.instant();
        for (RetainedMessage retainedMessage : retainedMessageRegistry.findMatching(subscriptionBinding.topicFilter())) {
            if (retainedMessage.properties().messageExpiry().isExpired(now)) {
                retainedMessageRegistry.removeRetained(retainedMessage.topicName());
                continue;
            }
            MqttQoS deliveryQos = grantedDeliveryQos(retainedMessage.qos().value(), subscriptionBinding.grantedQos());
            if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
                if (!canSendPublish(subscriptionBinding.clientId(), retainedMessage.topicName(),
                        retainedMessage.payloadCopy(), MqttQoS.AT_MOST_ONCE, true, false,
                        retainedMessage.properties(), subscriptionBinding.subscriptionIdentifiers())) {
                    continue;
                }
                deliveries.add(new PublishDelivery(
                        subscriptionBinding.clientId(),
                        retainedMessage.topicName(),
                        retainedMessage.payloadCopy(),
                        MqttQoS.AT_MOST_ONCE,
                        true,
                        false,
                        null,
                        false,
                        retainedMessage.properties(),
                        subscriptionBinding.subscriptionIdentifiers()));
                continue;
            }

            if (!canSendPublish(subscriptionBinding.clientId(), retainedMessage.topicName(),
                    retainedMessage.payloadCopy(), deliveryQos, true, false,
                    retainedMessage.properties(), subscriptionBinding.subscriptionIdentifiers())) {
                continue;
            }
            InflightMessage inflightMessage = sessionRegistry.createInflightMessage(
                            subscriptionBinding.clientId(),
                            retainedMessage.topicName(),
                            retainedMessage.payloadCopy(),
                            deliveryQos,
                            true,
                            false,
                            false,
                            retainedMessage.properties(),
                            subscriptionBinding.subscriptionIdentifiers())
                    .orElse(null);
            if (inflightMessage == null) {
                sessionRegistry.enqueuePendingOutboundMessage(subscriptionBinding.clientId(), new QueuedMessage(
                        retainedMessage.topicName(),
                        retainedMessage.payloadCopy(),
                        deliveryQos,
                        true,
                        false,
                        retainedMessage.properties(),
                        subscriptionBinding.subscriptionIdentifiers()));
            } else {
                deliveries.add(toPublishDelivery(subscriptionBinding.clientId(), inflightMessage));
            }
        }
        return deliveries;
    }

    private DeliveryPlan drainPendingDeliveries(String clientId) {
        List<PublishDelivery> deliveries = drainPendingInflight(clientId, clock.instant())
                .stream()
                .map(inflightMessage -> toPublishDelivery(clientId, inflightMessage))
                .toList();
        return deliveries.isEmpty() ? DeliveryPlan.empty() : DeliveryPlan.of(deliveries, 0);
    }

    private List<InflightMessage> drainQueuedMessagesForResume(String clientId, Instant now) {
        List<InflightMessage> deliverable = new ArrayList<>();
        while (true) {
            List<InflightMessage> drained = sessionRegistry.drainQueuedMessages(clientId, now);
            if (drained.isEmpty()) {
                return deliverable;
            }
            for (InflightMessage inflightMessage : drained) {
                if (canSendPublish(clientId, inflightMessage)) {
                    deliverable.add(inflightMessage);
                } else {
                    clearOutboundInflight(clientId, inflightMessage);
                }
            }
        }
    }

    private List<InflightMessage> drainPendingInflight(String clientId, Instant now) {
        List<InflightMessage> deliverable = new ArrayList<>();
        while (true) {
            List<InflightMessage> drained = sessionRegistry.drainPendingOutboundMessages(clientId, now);
            if (drained.isEmpty()) {
                return deliverable;
            }
            for (InflightMessage inflightMessage : drained) {
                if (canSendPublish(clientId, inflightMessage)) {
                    deliverable.add(inflightMessage);
                } else {
                    clearOutboundInflight(clientId, inflightMessage);
                }
            }
        }
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
                inflightMessage.properties(),
                inflightMessage.subscriptionIdentifiers());
    }

    private boolean canSendPublish(String clientId, InflightMessage inflightMessage) {
        return canSendPublish(
                clientId,
                inflightMessage.topicName(),
                inflightMessage.payload(),
                inflightMessage.qos(),
                inflightMessage.retain(),
                inflightMessage.duplicate(),
                inflightMessage.properties(),
                inflightMessage.subscriptionIdentifiers());
    }

    private boolean canSendPublish(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            PublishProperties properties,
            List<Integer> subscriptionIdentifiers) {
        ClientSession session = sessionRegistry.find(clientId).orElse(null);
        if (session == null) {
            return true;
        }
        int propertiesSize = MqttPacketSizeEstimator.publishPropertiesSize(properties)
                + MqttPacketSizeEstimator.subscriptionIdentifiersPropertiesSize(subscriptionIdentifiers);
        int packetSize = MqttPacketSizeEstimator.publishPacketSize(
                topicName,
                payload == null ? 0 : payload.length,
                qos.value(),
                propertiesSize);
        return packetSize <= session.maximumPacketSize();
    }

    private void clearOutboundInflight(String clientId, InflightMessage inflightMessage) {
        if (inflightMessage.qos() == MqttQoS.AT_LEAST_ONCE) {
            sessionRegistry.acknowledge(clientId, inflightMessage.packetId());
            return;
        }
        if (inflightMessage.qos() == MqttQoS.EXACTLY_ONCE) {
            sessionRegistry.completeOutboundQos2(clientId, inflightMessage.packetId());
        }
    }

    private byte[] copyPayload(byte[] payload) {
        return payload == null ? null : payload.clone();
    }

    private static String diagnosticSuffix(AuthnResult result) {
        return result.message() == null ? "" : " (" + result.message() + ")";
    }

    private static String diagnosticSuffix(AuthzResult result) {
        return result.message() == null ? "" : " (" + result.message() + ")";
    }

    private static int receiveMaximum(BrokerRuntimeConfig config) {
        return config == null ? 65_535 : validateReceiveMaximum(config.receiveMaximum());
    }

    private static int maximumPacketSize(BrokerRuntimeConfig config) {
        return config == null ? 268_435_455 : validateMaximumPacketSize(config.maxMessageSize());
    }

    private static int validateReceiveMaximum(int receiveMaximum) {
        if (receiveMaximum < 1 || receiveMaximum > 65_535) {
            throw new IllegalArgumentException("receiveMaximum must be between 1 and 65535");
        }
        return receiveMaximum;
    }

    private static int validateMaximumPacketSize(int maximumPacketSize) {
        if (maximumPacketSize < 1 || maximumPacketSize > 268_435_455) {
            throw new IllegalArgumentException("maximumPacketSize must be between 1 and 268435455");
        }
        return maximumPacketSize;
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

    private void restoreSessionSubscription(
            String clientId,
            String topicFilter,
            SubscriptionBinding previousSubscription) {
        if (previousSubscription == null) {
            sessionRegistry.removeSubscription(clientId, topicFilter);
            return;
        }
        sessionRegistry.addSubscription(previousSubscription);
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

        PublishRoutingResult routingResult = routeServerPublish(connection, new PublishRequest(
                willMessage.topicName(),
                0,
                willMessage.qos().value(),
                willMessage.retain(),
                false,
                willMessage.payloadCopy(),
                willMessage.properties()));
        return routingResult.deliveries();
    }
}
