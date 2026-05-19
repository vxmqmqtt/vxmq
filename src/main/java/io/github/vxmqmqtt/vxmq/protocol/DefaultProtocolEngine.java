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
import io.github.vxmqmqtt.vxmq.observability.BrokerDiagnosticEvent;
import io.github.vxmqmqtt.vxmq.observability.BrokerDiagnosticSeverity;
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
import io.github.vxmqmqtt.vxmq.protocol.model.OutboundPubRecOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgement;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.RejectedConnectResponse;
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
import io.github.vxmqmqtt.vxmq.retained.RetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import io.github.vxmqmqtt.vxmq.session.ClientSession;
import io.github.vxmqmqtt.vxmq.session.InboundQos2Message;
import io.github.vxmqmqtt.vxmq.session.SessionOpenRequest;
import io.github.vxmqmqtt.vxmq.session.SessionOpenResult;
import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ConnectionState;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
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
    private final SubscriptionRegistry subscriptionRegistry;
    private final MqttTopicSupport mqttTopicSupport;
    private final BrokerEventSink brokerEventSink;
    private final ClientConnectionRegistry connectionRegistry;
    private final ProtocolDiagnostics diagnostics;
    private final MqttProtocolValidator validator;
    private final SessionLifecycleCoordinator sessionLifecycle;
    private final PublishDeliveryCoordinator publishDeliveryCoordinator;
    private final WillService willService;
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
        this.subscriptionRegistry = subscriptionRegistry;
        this.mqttTopicSupport = mqttTopicSupport;
        this.brokerEventSink = brokerEventSink;
        this.connectionRegistry = connectionRegistry;
        this.diagnostics = new ProtocolDiagnostics(brokerEventSink);
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.validator = new MqttProtocolValidator(mqttTopicSupport, brokerReceiveMaximum, brokerMaximumPacketSize);
        this.sessionLifecycle = new SessionLifecycleCoordinator(sessionRegistry, subscriptionRegistry);
        this.publishDeliveryCoordinator = new PublishDeliveryCoordinator(
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                connectionRegistry,
                brokerEventSink,
                sessionLifecycle,
                diagnostics,
                this.clock);
        this.willService = new WillService(sessionRegistry, publishDeliveryCoordinator);
        this.brokerReceiveMaximum = validator.brokerReceiveMaximum();
        this.brokerMaximumPacketSize = validator.brokerMaximumPacketSize();
    }

    @Override
    public ConnectOutcome handleConnect(ClientConnection connection, ConnectRequest request) {
        // Reject unsupported protocol names or versions before any state is mutated.
        if (!"MQTT".equals(request.protocolName()) || (!request.isMqtt311() && !request.isMqtt5())) {
            MqttConnectReturnCode returnCode = rejectUnsupportedProtocolVersion(request);
            diagnostics.diagnostic(connection, "connect_rejected", "CONNECT", "UNSUPPORTED_PROTOCOL_VERSION")
                    .mqttReturnCode(returnCode)
                    .buildDiagnostic();
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    returnCode,
                    MqttProperties.NO_PROPERTIES));
        }
        if (validator.hasInvalidConnectProperties(request)) {
            diagnostics.diagnostic(connection, "connect_rejected", "CONNECT", "INVALID_CONNECT_PROPERTIES")
                    .mqttReturnCode(MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR)
                    .buildDiagnostic();
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR,
                    MqttProperties.NO_PROPERTIES));
        }
        if (validator.hasInvalidWill(request)) {
            diagnostics.diagnostic(connection, "connect_rejected", "CONNECT", "INVALID_WILL")
                    .mqttReturnCode(MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR)
                    .topic(request.willMessage() == null ? null : request.willMessage().topicName())
                    .buildDiagnostic();
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR,
                    MqttProperties.NO_PROPERTIES));
        }

        AuthnResult authnResult = authnProvider.authenticate(connection, request);
        if (!authnResult.allowed()) {
            MqttConnectReturnCode returnCode = rejectNotAuthorized(request);
            diagnostics.diagnostic(connection, "connect_rejected", "CONNECT", authnResult.reason())
                    .mqttReturnCode(returnCode)
                    .buildDiagnostic();
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    returnCode,
                    MqttProperties.NO_PROPERTIES));
        }

        String effectiveClientId = resolveClientId(request);
        if (effectiveClientId == null) {
            MqttConnectReturnCode returnCode = rejectInvalidClientId(request);
            diagnostics.diagnostic(connection, "connect_rejected", "CONNECT", "CLIENT_ID_REJECTED")
                    .mqttReturnCode(returnCode)
                    .buildDiagnostic();
            return ConnectOutcome.rejected(new RejectedConnectResponse(
                    returnCode,
                    MqttProperties.NO_PROPERTIES));
        }

        AuthzResult willAuthzResult = authorizeWillPublish(
                connection,
                effectiveClientId,
                authnResult.principal(),
                request);
        if (!willAuthzResult.allowed()) {
            MqttConnectReturnCode returnCode = rejectNotAuthorized(request);
            diagnostics.diagnostic(connection, "connect_rejected", "CONNECT", willAuthzResult.reason())
                    .mqttReturnCode(returnCode)
                    .topic(request.willMessage() == null ? null : request.willMessage().topicName())
                    .buildDiagnostic();
            return ConnectOutcome.rejected(new RejectedConnectResponse(returnCode, MqttProperties.NO_PROPERTIES));
        }

        MqttProperties responseProperties = buildConnectResponseProperties(request, effectiveClientId);
        SessionOpenResult sessionOpenResult = sessionRegistry.openSession(
                effectiveClientId,
                buildSessionOpenRequest(request, connection.connectionId()));
        sessionLifecycle.clearRoutingBindings(sessionOpenResult.clearedSession());
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
        if (validator.hasInvalidSubscriptionProperties(request.properties())) {
            diagnostics.diagnostic(connection, "subscribe_rejected", "SUBSCRIBE", "INVALID_SUBSCRIBE_PROPERTIES")
                    .mqttReasonCode(MqttDisconnectReasonCode.PROTOCOL_ERROR)
                    .buildDiagnostic();
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
                diagnostics.diagnostic(connection, "subscribe_item_rejected", "SUBSCRIBE", "TOPIC_FILTER_INVALID")
                        .mqttReasonCode(MqttSubAckReasonCode.TOPIC_FILTER_INVALID)
                        .topicFilter(topicFilter)
                        .qos(item.requestedQos())
                        .buildDiagnostic();
                results.add(SubscriptionItemResult.rejected(topicFilter, MqttSubAckReasonCode.TOPIC_FILTER_INVALID));
                continue;
            }

            if (!isSupportedRequestedQos(item.requestedQos())) {
                diagnostics.diagnostic(connection, "subscribe_item_rejected", "SUBSCRIBE", "QOS_NOT_SUPPORTED")
                        .mqttReasonCode(MqttSubAckReasonCode.IMPLEMENTATION_SPECIFIC_ERROR)
                        .topicFilter(topicFilter)
                        .qos(item.requestedQos())
                        .buildDiagnostic();
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
                diagnostics.diagnostic(connection, "subscribe_item_rejected", "SUBSCRIBE", authzResult.reason())
                        .mqttReasonCode(MqttSubAckReasonCode.NOT_AUTHORIZED)
                        .topicFilter(topicFilter)
                        .qos(item.requestedQos())
                        .buildDiagnostic();
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
                if (publishDeliveryCoordinator.shouldReplayRetained(item.retainHandling(), subscriptionAlreadyExisted)) {
                    retainedDeliveries.addAll(publishDeliveryCoordinator.buildRetainedDeliveries(subscriptionBinding));
                }
            } catch (RuntimeException exception) {
                sessionLifecycle.restoreSessionSubscription(
                        connection.effectiveClientId(),
                        topicFilter,
                        previousSubscription);
                diagnostics.diagnostic(connection, "subscribe_item_rejected", "SUBSCRIBE", "SUBSCRIPTION_REGISTRY_FAILURE")
                        .severity(BrokerDiagnosticSeverity.ERROR)
                        .mqttReasonCode(MqttSubAckReasonCode.UNSPECIFIED_ERROR)
                        .topicFilter(topicFilter)
                        .qos(item.requestedQos())
                        .buildDiagnostic();
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
                diagnostics.diagnostic(connection, "unsubscribe_item_rejected", "UNSUBSCRIBE", "TOPIC_FILTER_INVALID")
                        .mqttReasonCode(MqttUnsubAckReasonCode.TOPIC_FILTER_INVALID)
                        .topicFilter(topicFilter)
                        .buildDiagnostic();
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
                diagnostics.diagnostic(connection, "unsubscribe_item_rejected", "UNSUBSCRIBE", "SUBSCRIPTION_REGISTRY_FAILURE")
                        .severity(BrokerDiagnosticSeverity.ERROR)
                        .mqttReasonCode(MqttUnsubAckReasonCode.UNSPECIFIED_ERROR)
                        .topicFilter(topicFilter)
                        .buildDiagnostic();
                results.add(UnsubscribeItemResult.rejected(topicFilter, MqttUnsubAckReasonCode.UNSPECIFIED_ERROR));
            }
        }
        return new UnsubscribeAck(results);
    }

    @Override
    public InboundPublishOutcome handlePublish(ClientConnection connection, PublishRequest request) {
        if (!mqttTopicSupport.isValidTopicName(request.topicName())) {
            diagnostics.publishRejected(connection, request, "TOPIC_NAME_INVALID", MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
        }

        if (request.qos() < 0 || request.qos() > 2) {
            diagnostics.publishRejected(connection, request, "QOS_NOT_SUPPORTED", MqttDisconnectReasonCode.QOS_NOT_SUPPORTED);
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED);
        }

        if (validator.hasInvalidPublishProperties(connection.protocolVersion(), request.properties())) {
            diagnostics.publishRejected(connection, request, "INVALID_PUBLISH_PROPERTIES", MqttDisconnectReasonCode.PROTOCOL_ERROR);
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.PROTOCOL_ERROR);
        }

        if (validator.isPublishPacketTooLarge(connection.protocolVersion(), request.packetSize())) {
            diagnostics.publishRejected(connection, request, "PACKET_TOO_LARGE", MqttDisconnectReasonCode.PACKET_TOO_LARGE);
            return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.PACKET_TOO_LARGE);
        }

        AuthzResult authzResult = authzProvider.authorize(new AuthzContext(
                connection,
                connection.effectiveClientId(),
                connection.principal(),
                AuthzAction.PUBLISH,
                request.topicName()));
        if (!authzResult.allowed()) {
            diagnostics.publishRejected(connection, request, authzResult.reason(),
                    rejectUnauthorizedPublishReasonCode(connection, request.qos()));
            return rejectUnauthorizedPublish(connection, request);
        }

        if (request.qos() == 2) {
            if (connection.effectiveClientId() == null || request.packetId() <= 0) {
                diagnostics.publishRejected(connection, request, "PACKET_IDENTIFIER_INVALID", MqttDisconnectReasonCode.PROTOCOL_ERROR);
                return InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.PROTOCOL_ERROR);
            }
            if (sessionRegistry.find(connection.effectiveClientId())
                    .map(ClientSession::inboundQos2MessageCount)
                    .orElse(0) >= brokerReceiveMaximum
                    && !sessionRegistry.hasInboundQos2Message(connection.effectiveClientId(), request.packetId())) {
                diagnostics.publishRejected(connection, request, "RECEIVE_MAXIMUM_EXCEEDED",
                        MqttDisconnectReasonCode.RECEIVE_MAXIMUM_EXCEEDED);
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

        PublishDeliveryCoordinator.PublishRoutingResult routingResult =
                publishDeliveryCoordinator.routePublish(connection, request);
        PublishAcknowledgement acknowledgement = request.qos() == 1
                ? PublishAcknowledgement.pubAck(MqttPubAckReasonCode.SUCCESS)
                : PublishAcknowledgement.none();
        return InboundPublishOutcome.completed(
                DeliveryPlan.of(routingResult.deliveries(), routingResult.queuedMessageCount()),
                acknowledgement);
    }

    @Override
    public SessionResumePlan handleSessionResume(ClientConnection connection) {
        return publishDeliveryCoordinator.handleSessionResume(connection);
    }

    @Override
    public DeliveryPlan handlePubAck(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.acknowledge(connection.effectiveClientId(), packetId);
            return publishDeliveryCoordinator.drainPendingDeliveries(connection.effectiveClientId());
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

        PublishDeliveryCoordinator.PublishRoutingResult routingResult = publishDeliveryCoordinator.routePublish(connection, new PublishRequest(
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
            return publishDeliveryCoordinator.drainPendingDeliveries(connection.effectiveClientId());
        }
        return DeliveryPlan.empty();
    }

    @Override
    public void handleDisconnect(ClientConnection connection) {
        brokerEventSink.diagnostic(BrokerDiagnosticEvent.builder("connection_disconnect")
                .severity(BrokerDiagnosticSeverity.INFO)
                .operation("DISCONNECT")
                .reason("CLIENT_DISCONNECT")
                .connection(connection)
                .willPublished(false)
                .build());
        willService.discardForDisconnect(connection);
        connection.transitionTo(ConnectionState.DISCONNECTING);
    }

    @Override
    public List<PublishDelivery> handleConnectionClosed(ClientConnection connection) {
        List<PublishDelivery> willDeliveries = List.of();
        boolean willPublished = false;
        WillService.WillPublishResult willPublishResult = willService.publishOnAbnormalClose(connection);
        willDeliveries = willPublishResult.deliveries();
        willPublished = willPublishResult.willPublished();
        if (connection.effectiveClientId() != null) {
            ClientSession clearedSession = sessionRegistry
                    .onConnectionClosed(connection.effectiveClientId(), connection.connectionId())
                    .orElse(null);
            sessionLifecycle.clearRoutingBindings(clearedSession);
            brokerEventSink.diagnostic(BrokerDiagnosticEvent.builder("connection_closed")
                    .severity(BrokerDiagnosticSeverity.INFO)
                    .operation("CLOSE")
                    .reason(connection.state() == ConnectionState.DISCONNECTING ? "CLIENT_DISCONNECT" : "SOCKET_CLOSED")
                    .connection(connection)
                    .willPublished(willPublished)
                    .sessionRemoved(clearedSession != null)
                    .matchedClients(willDeliveries.size())
                    .build());
        } else {
            brokerEventSink.diagnostic(BrokerDiagnosticEvent.builder("connection_closed")
                    .severity(BrokerDiagnosticSeverity.INFO)
                    .operation("CLOSE")
                    .reason("SOCKET_CLOSED")
                    .connection(connection)
                    .willPublished(willPublished)
                    .matchedClients(willDeliveries.size())
                    .build());
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

    private Object rejectUnauthorizedPublishReasonCode(ClientConnection connection, int qos) {
        if (connection.protocolVersion() != 5) {
            return null;
        }
        if (qos == 1) {
            return MqttPubAckReasonCode.NOT_AUTHORIZED;
        }
        if (qos == 2) {
            return MqttPubRecReasonCode.NOT_AUTHORIZED;
        }
        return MqttDisconnectReasonCode.NOT_AUTHORIZED;
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

    private static int receiveMaximum(BrokerRuntimeConfig config) {
        return config == null ? 65_535 : MqttProtocolValidator.validateReceiveMaximum(config.receiveMaximum());
    }

    private static int maximumPacketSize(BrokerRuntimeConfig config) {
        return config == null ? 268_435_455 : MqttProtocolValidator.validateMaximumPacketSize(config.maxMessageSize());
    }

}
