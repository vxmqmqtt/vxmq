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
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.TopicMatcher;
import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ConnectionState;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

/**
 * Default in-memory protocol engine for the current single-node milestone.
 */
@ApplicationScoped
public class DefaultProtocolEngine implements ProtocolEngine {

    private final AuthProvider authProvider;
    private final SessionRegistry sessionRegistry;
    private final SubscriptionRegistry subscriptionRegistry;
    private final TopicMatcher topicMatcher;
    private final BrokerEventSink brokerEventSink;
    private final ClientConnectionRegistry connectionRegistry;

    public DefaultProtocolEngine(
            AuthProvider authProvider,
            SessionRegistry sessionRegistry,
            SubscriptionRegistry subscriptionRegistry,
            TopicMatcher topicMatcher,
            BrokerEventSink brokerEventSink,
            ClientConnectionRegistry connectionRegistry) {
        this.authProvider = authProvider;
        this.sessionRegistry = sessionRegistry;
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
        connection.assignClientId(effectiveClientId);
        connection.transitionTo(ConnectionState.CONNECTED);
        // A new connection with the same client identifier replaces the old one.
        String supersededConnectionId = connectionRegistry.bindClientId(effectiveClientId, connection.internalId())
                .orElse(null);
        sessionRegistry.bindConnection(effectiveClientId, connection.internalId());
        brokerEventSink.connectionAccepted(connection);
        return ConnectDecision.accept(effectiveClientId, responseProperties, supersededConnectionId);
    }

    @Override
    public SubscribeResult handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
        List<SubscriptionItemResult> results = new ArrayList<>();
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

            try {
                sessionRegistry.addSubscription(connection.effectiveClientId(), topicFilter);
                subscriptionRegistry.addSubscription(new SubscriptionBinding(
                        connection.effectiveClientId(),
                        topicFilter,
                        item.requestedQos()));
                brokerEventSink.subscriptionAdded(connection, topicFilter);
                results.add(SubscriptionItemResult.granted(topicFilter, MqttQoS.AT_MOST_ONCE));
            } catch (RuntimeException exception) {
                // Roll back the session view if the routing registry write fails.
                sessionRegistry.removeSubscription(connection.effectiveClientId(), topicFilter);
                brokerEventSink.protocolWarning(connection, "Failed to register subscription: " + topicFilter);
                results.add(SubscriptionItemResult.rejected(topicFilter, MqttSubAckReasonCode.UNSPECIFIED_ERROR));
            }
        }
        return new SubscribeResult(results);
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
            return PublishResult.rejected(MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
        }

        if (request.qos() != 0) {
            brokerEventSink.protocolWarning(connection, "Rejected unsupported inbound QoS: " + request.qos());
            return PublishResult.rejected(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED);
        }

        List<PublishDelivery> deliveries = subscriptionRegistry.match(request.topicName())
                .stream()
                .map(binding -> new PublishDelivery(binding.clientId(), MqttQoS.AT_MOST_ONCE))
                .toList();

        int matchedClients = deliveries.size();
        brokerEventSink.messageRouted(connection, request.topicName(), matchedClients);
        return PublishResult.accepted(deliveries);
    }

    @Override
    public void handleDisconnect(ClientConnection connection) {
        connection.transitionTo(ConnectionState.DISCONNECTING);
        if (connection.effectiveClientId() != null) {
            sessionRegistry.unbindConnection(connection.effectiveClientId(), connection.internalId());
        }
    }

    @Override
    public void handleConnectionClosed(ClientConnection connection) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.unbindConnection(connection.effectiveClientId(), connection.internalId());
        }
        connection.transitionTo(ConnectionState.CLOSED);
    }

    private String resolveClientId(ConnectRequest request) {
        // Explicit client identifiers always win over auto-assignment.
        if (request.requestedClientId() != null && !request.requestedClientId().isBlank()) {
            return request.requestedClientId();
        }

        // MQTT 3.1.1 requires a persistent session to carry a non-empty client identifier.
        if (request.isMqtt311() && !request.cleanSession()) {
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
}
