package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.auth.AuthProvider;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectDecision;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItem;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItemResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeItemResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessage;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.TopicMatcher;
import io.github.vxmqmqtt.vxmq.session.ClientSession;
import io.github.vxmqmqtt.vxmq.session.InflightMessage;
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
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
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
    private final TopicMatcher topicMatcher;
    private final BrokerEventSink brokerEventSink;
    private final ClientConnectionRegistry connectionRegistry;

    public DefaultProtocolEngine(
            AuthProvider authProvider,
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            TopicMatcher topicMatcher,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry) {
        this.authProvider = authProvider;
        this.sessionRegistry = sessionRegistry;
        this.retainedMessageRegistry = retainedMessageRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.topicMatcher = topicMatcher;
        this.brokerEventSink = brokerEventSink;
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public ConnectDecision handleConnect(ClientConnection connection, ConnectRequest request) {
        // Reject unsupported protocol names or versions before any state is mutated.
        if (!"MQTT".equals(request.protocolName()) || (!request.isMqtt311() && !request.isMqtt5())) {
            brokerEventSink.protocolWarning(connection, "Unsupported protocol version: " + request.protocolVersion());
            return ConnectDecision.reject(rejectUnsupportedProtocolVersion(request));
        }

        if (!authProvider.allowConnect(connection, request)) {
            brokerEventSink.protocolWarning(connection, "Connection rejected by auth provider");
            return ConnectDecision.reject(rejectNotAuthorized(request));
        }

        String effectiveClientId = resolveClientId(request);
        if (effectiveClientId == null) {
            brokerEventSink.protocolWarning(connection, "Client identifier rejected");
            return ConnectDecision.reject(rejectInvalidClientId(request));
        }

        MqttProperties responseProperties = buildConnectResponseProperties(request, effectiveClientId);
        SessionOpenResult sessionOpenResult = sessionRegistry.openSession(
                effectiveClientId,
                buildSessionOpenRequest(request, connection.connectionId()));
        clearRoutingBindings(sessionOpenResult.clearedSession());
        connection.assignClientId(effectiveClientId);
        connection.transitionTo(ConnectionState.CONNECTED);
        // A new connection with the same client identifier replaces the old one.
        String supersededConnectionId = connectionRegistry.bindClientId(effectiveClientId, connection.connectionId())
                .orElse(null);
        brokerEventSink.connectionAccepted(connection);
        return ConnectDecision.accept(
                sessionOpenResult.sessionPresent(),
                effectiveClientId,
                responseProperties,
                supersededConnectionId);
    }

    @Override
    public SubscribeResult handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
        List<SubscriptionItemResult> results = new ArrayList<>();
        List<PublishDelivery> retainedDeliveries = new ArrayList<>();
        for (SubscriptionItem item : request.items()) {
            String topicFilter = item.topicFilter();
            if (!topicMatcher.isValidFilter(topicFilter)) {
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
            try {
                sessionRegistry.addSubscription(connection.effectiveClientId(), topicFilter, grantedQos);
                subscriptionRegistry.addSubscription(new SubscriptionBinding(
                        connection.effectiveClientId(),
                        topicFilter,
                        grantedQos));
                brokerEventSink.subscriptionAdded(connection, topicFilter);
                results.add(SubscriptionItemResult.granted(topicFilter, grantedQos));
                retainedDeliveries.addAll(buildRetainedDeliveries(connection.effectiveClientId(), topicFilter, grantedQos));
            } catch (RuntimeException exception) {
                // Roll back the session view if the routing registry write fails.
                sessionRegistry.removeSubscription(connection.effectiveClientId(), topicFilter);
                brokerEventSink.protocolWarning(connection, "Failed to register subscription: " + topicFilter);
                results.add(SubscriptionItemResult.rejected(topicFilter, MqttSubAckReasonCode.UNSPECIFIED_ERROR));
            }
        }
        return new SubscribeResult(results, retainedDeliveries);
    }

    @Override
    public UnsubscribeResult handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request) {
        List<UnsubscribeItemResult> results = new ArrayList<>();
        for (String topicFilter : request.topicFilters()) {
            if (!topicMatcher.isValidFilter(topicFilter)) {
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
        return new UnsubscribeResult(results);
    }

    @Override
    public PublishResult handlePublish(ClientConnection connection, PublishRequest request) {
        if (!topicMatcher.isValidTopicName(request.topicName())) {
            brokerEventSink.protocolWarning(connection, "Rejected publish with invalid topic name: " + request.topicName());
            return PublishResult.rejectedWithDisconnect(MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
        }

        if (request.qos() < 0 || request.qos() > 1) {
            brokerEventSink.protocolWarning(connection, "Rejected unsupported inbound QoS: " + request.qos());
            return PublishResult.rejectedWithDisconnect(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED);
        }

        updateRetainedMessageIfRequested(request);

        List<PublishDelivery> deliveries = new ArrayList<>();
        int queuedMessageCount = 0;
        for (SubscriptionBinding binding : subscriptionRegistry.match(request.topicName())) {
            MqttQoS deliveryQos = grantedDeliveryQos(request.qos(), binding.grantedQos());
            boolean online = connectionRegistry.findActiveConnectionId(binding.clientId()).isPresent();
            if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
                if (online) {
                    deliveries.add(new PublishDelivery(
                            binding.clientId(),
                            request.topicName(),
                            copyPayload(request.payload()),
                            MqttQoS.AT_MOST_ONCE,
                            request.retain(),
                            false,
                            null,
                            false));
                }
                continue;
            }

            if (online) {
                sessionRegistry.createInflightMessage(
                                binding.clientId(),
                                request.topicName(),
                                request.payload(),
                                MqttQoS.AT_LEAST_ONCE,
                                request.retain(),
                                false,
                                false)
                        .map(inflightMessage -> toPublishDelivery(binding.clientId(), inflightMessage))
                        .ifPresent(deliveries::add);
                continue;
            }

            ClientSession session = sessionRegistry.find(binding.clientId()).orElse(null);
            if (session != null && session.persistent()) {
                sessionRegistry.enqueueOfflineMessage(binding.clientId(), new QueuedMessage(
                        request.topicName(),
                        copyPayload(request.payload()),
                        MqttQoS.AT_LEAST_ONCE,
                        request.retain(),
                        false));
                queuedMessageCount++;
            }
        }

        int matchedClients = deliveries.size() + queuedMessageCount;
        brokerEventSink.messageRouted(connection, request.topicName(), matchedClients);
        return PublishResult.accepted(
                deliveries,
                queuedMessageCount,
                request.qos() == 1,
                request.qos() == 1 ? MqttPubAckReasonCode.SUCCESS : null);
    }

    @Override
    public List<PublishDelivery> handleSessionResume(ClientConnection connection) {
        if (connection.effectiveClientId() == null) {
            return List.of();
        }

        ClientSession session = sessionRegistry.find(connection.effectiveClientId()).orElse(null);
        if (session == null || !connection.connectionId().equals(session.connectionId())) {
            return List.of();
        }

        return sessionRegistry.drainQueuedMessages(connection.effectiveClientId())
                .stream()
                .map(inflightMessage -> toPublishDelivery(connection.effectiveClientId(), inflightMessage))
                .toList();
    }

    @Override
    public void handlePubAck(ClientConnection connection, int packetId) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.acknowledge(connection.effectiveClientId(), packetId);
        }
    }

    @Override
    public void handleDisconnect(ClientConnection connection) {
        connection.transitionTo(ConnectionState.DISCONNECTING);
    }

    @Override
    public void handleConnectionClosed(ClientConnection connection) {
        if (connection.effectiveClientId() != null) {
            clearRoutingBindings(sessionRegistry.onConnectionClosed(connection.effectiveClientId(), connection.connectionId())
                    .orElse(null));
        }
        connection.transitionTo(ConnectionState.CLOSED);
    }

    private SessionOpenRequest buildSessionOpenRequest(ConnectRequest request, String connectionId) {
        // MQTT 3.1.1 and MQTT 5 share the same open/restore flow, but differ in persistence semantics.
        Long sessionExpiryIntervalSeconds = request.isMqtt5() ? request.mqtt5SessionExpiryIntervalSeconds() : null;
        return new SessionOpenRequest(
                request.startsFreshSession(),
                request.retainsSessionOnDisconnect(),
                sessionExpiryIntervalSeconds,
                connectionId);
    }

    private String resolveClientId(ConnectRequest request) {
        // Explicit client identifiers always win over auto-assignment.
        if (request.requestedClientId() != null && !request.requestedClientId().isBlank()) {
            return request.requestedClientId();
        }

        // MQTT 3.1.1 requires a persistent session to carry a non-empty client identifier.
        if (request.isMqtt311() && !request.mqtt311CleanSession()) {
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

    private MqttQoS grantedSubscriptionQos(int requestedQos) {
        if (requestedQos <= 0) {
            return MqttQoS.AT_MOST_ONCE;
        }
        return MqttQoS.AT_LEAST_ONCE;
    }

    private MqttQoS grantedDeliveryQos(int publishQos, MqttQoS subscriptionQos) {
        int value = Math.min(publishQos, subscriptionQos.value());
        return value == 0 ? MqttQoS.AT_MOST_ONCE : MqttQoS.AT_LEAST_ONCE;
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
                request.qos() == 0 ? MqttQoS.AT_MOST_ONCE : MqttQoS.AT_LEAST_ONCE);
    }

    private List<PublishDelivery> buildRetainedDeliveries(String clientId, String topicFilter, MqttQoS grantedQos) {
        List<PublishDelivery> deliveries = new ArrayList<>();
        for (RetainedMessage retainedMessage : retainedMessageRegistry.findMatching(topicFilter)) {
            MqttQoS deliveryQos = grantedDeliveryQos(retainedMessage.qos().value(), grantedQos);
            if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
                deliveries.add(new PublishDelivery(
                        clientId,
                        retainedMessage.topicName(),
                        retainedMessage.payloadCopy(),
                        MqttQoS.AT_MOST_ONCE,
                        true,
                        false,
                        null,
                        false));
                continue;
            }

            sessionRegistry.createInflightMessage(
                            clientId,
                            retainedMessage.topicName(),
                            retainedMessage.payloadCopy(),
                            MqttQoS.AT_LEAST_ONCE,
                            true,
                            false,
                            false)
                    .map(inflightMessage -> toPublishDelivery(clientId, inflightMessage))
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
                inflightMessage.fromOfflineQueue());
    }

    private byte[] copyPayload(byte[] payload) {
        return payload == null ? null : payload.clone();
    }

    private void clearRoutingBindings(ClientSession clearedSession) {
        if (clearedSession == null) {
            return;
        }

        for (String topicFilter : clearedSession.subscriptions()) {
            subscriptionRegistry.removeSubscription(clearedSession.clientId(), topicFilter);
        }
    }
}
