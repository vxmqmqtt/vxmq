package io.github.vxmqmqtt.vxmq.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.auth.PermitAllAuthProvider;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectDecision;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItem;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.routing.DefaultTopicMatcher;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.session.InMemorySessionRegistry;
import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ConnectionState;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for protocol decisions without starting the transport layer.
 */
class DefaultProtocolEngineTest {

    private ClientConnectionRegistry connectionRegistry;
    private SessionRegistry sessionRegistry;
    private SubscriptionRegistry subscriptionRegistry;
    private DefaultProtocolEngine protocolEngine;

    @BeforeEach
    void setUp() {
        DefaultTopicMatcher topicMatcher = new DefaultTopicMatcher();
        connectionRegistry = new ClientConnectionRegistry();
        sessionRegistry = new InMemorySessionRegistry();
        subscriptionRegistry = new InMemorySubscriptionRegistry(topicMatcher);
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthProvider(),
                sessionRegistry,
                subscriptionRegistry,
                topicMatcher,
                new NoOpBrokerEventSink(),
                connectionRegistry);
    }

    // Verifies that MQTT 3.1.1 rejects empty client ids when the session is not clean.
    @Test
    void shouldRejectEmptyClientIdForPersistentMqtt311Session() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 4, false);

        ConnectDecision decision = protocolEngine.handleConnect(connection, new ConnectRequest(
                "",
                "MQTT",
                4,
                false,
                null,
                false));

        assertFalse(decision.accepted());
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, decision.returnCode());
    }

    // Verifies that MQTT 3.1.1 clean sessions can receive an auto-generated client id.
    @Test
    void shouldAssignClientIdForMqtt311CleanSession() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 4, true);

        ConnectDecision decision = protocolEngine.handleConnect(connection, new ConnectRequest(
                "",
                "MQTT",
                4,
                true,
                null,
                false));

        assertTrue(decision.accepted());
        assertNotNull(decision.effectiveClientId());
        assertTrue(decision.effectiveClientId().startsWith("vxmq-"));
        assertTrue(decision.responseProperties().isEmpty());
    }

    // Verifies that MQTT 5 returns Assigned Client Identifier when the broker generates the id.
    @Test
    void shouldAssignClientIdAndConnAckPropertyForMqtt5() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 5, true);

        ConnectDecision decision = protocolEngine.handleConnect(connection, new ConnectRequest(
                "",
                "MQTT",
                5,
                true,
                null,
                false));

        assertTrue(decision.accepted());
        assertNotNull(decision.effectiveClientId());
        MqttProperties.MqttProperty<?> assignedClientIdProperty = decision.responseProperties()
                .getProperty(MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value());
        assertNotNull(assignedClientIdProperty);
        assertEquals(decision.effectiveClientId(), assignedClientIdProperty.value());
    }

    // Verifies that a second connection with the same client id marks the previous one as superseded.
    @Test
    void shouldMarkPreviousConnectionForTakeOver() {
        ClientConnection firstConnection = connectionRegistry.open("127.0.0.1", "client-a", "MQTT", 5, true);
        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "client-a", "MQTT", 5, true);

        ConnectDecision firstDecision = protocolEngine.handleConnect(firstConnection, new ConnectRequest(
                "client-a",
                "MQTT",
                5,
                true,
                null,
                false));
        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, new ConnectRequest(
                "client-a",
                "MQTT",
                5,
                true,
                null,
                false));

        assertTrue(firstDecision.accepted());
        assertTrue(secondDecision.accepted());
        assertNull(firstDecision.supersededConnectionId());
        assertEquals(firstConnection.internalId(), secondDecision.supersededConnectionId());
    }

    // Verifies that valid subscriptions are accepted and normalized to the currently supported QoS 0 path.
    @Test
    void shouldGrantQos0ForValidSubscription() {
        ClientConnection connection = connectClient("client-sub", 5);

        SubscribeResult result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));

        assertEquals(1, result.itemResults().size());
        assertEquals(MqttQoS.AT_MOST_ONCE, result.itemResults().getFirst().grantedQos());
        assertEquals(MqttSubAckReasonCode.GRANTED_QOS0, result.itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-sub").orElseThrow().subscriptions().contains("sensors/+/temperature"));
        assertEquals(1, subscriptionRegistry.match("sensors/room-1/temperature").size());
    }

    // Verifies that invalid topic filters are rejected without mutating session state.
    @Test
    void shouldRejectInvalidSubscriptionFilter() {
        ClientConnection connection = connectClient("client-invalid-sub", 5);

        SubscribeResult result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/#/temperature", 0))));

        assertEquals(1, result.itemResults().size());
        assertFalse(result.itemResults().getFirst().accepted());
        assertEquals(MqttSubAckReasonCode.TOPIC_FILTER_INVALID, result.itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-invalid-sub").orElseThrow().subscriptions().isEmpty());
    }

    // Verifies that unsubscribe removes state from both the session registry and routing registry.
    @Test
    void shouldRemoveExistingSubscriptionOnUnsubscribe() {
        ClientConnection connection = connectClient("client-unsub", 5);
        protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        UnsubscribeResult result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/+/temperature")));

        assertEquals(1, result.itemResults().size());
        assertTrue(result.itemResults().getFirst().accepted());
        assertEquals(MqttUnsubAckReasonCode.SUCCESS, result.itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-unsub").orElseThrow().subscriptions().isEmpty());
        assertTrue(subscriptionRegistry.match("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that unsubscribing an unknown filter returns a non-error MQTT 5 reason code.
    @Test
    void shouldReportNoSubscriptionExistedOnUnsubscribe() {
        ClientConnection connection = connectClient("client-unsub-missing", 5);

        UnsubscribeResult result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/+/temperature")));

        assertEquals(1, result.itemResults().size());
        assertTrue(result.itemResults().getFirst().accepted());
        assertEquals(MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED, result.itemResults().getFirst().reasonCode());
    }

    // Verifies that invalid filters are rejected during unsubscribe as well.
    @Test
    void shouldRejectInvalidUnsubscribeFilter() {
        ClientConnection connection = connectClient("client-unsub-invalid", 5);

        UnsubscribeResult result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/#/temperature")));

        assertEquals(1, result.itemResults().size());
        assertFalse(result.itemResults().getFirst().accepted());
        assertEquals(MqttUnsubAckReasonCode.TOPIC_FILTER_INVALID, result.itemResults().getFirst().reasonCode());
    }

    // Verifies that a published message is routed to the matching subscriber set.
    @Test
    void shouldRoutePublishToMatchedSubscribers() {
        ClientConnection publisher = connectClient("publisher", 5);
        ClientConnection subscriber = connectClient("subscriber", 5);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        PublishResult result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                false,
                false,
                "payload".getBytes()));

        assertTrue(result.accepted());
        assertEquals(1, result.deliveries().size());
        assertEquals("subscriber", result.deliveries().getFirst().clientId());
        assertEquals(MqttQoS.AT_MOST_ONCE, result.deliveries().getFirst().grantedQos());
    }

    // Verifies that topic names containing subscription wildcards are rejected for publish.
    @Test
    void shouldRejectPublishWithInvalidTopicName() {
        ClientConnection publisher = connectClient("publisher-invalid-topic", 5);

        PublishResult result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/+/temperature",
                0,
                false,
                false,
                "payload".getBytes()));

        assertFalse(result.accepted());
        assertTrue(result.deliveries().isEmpty());
        assertEquals(MqttDisconnectReasonCode.TOPIC_NAME_INVALID, result.disconnectReasonCode());
    }

    // Verifies that inbound QoS levels above the M1 support boundary are rejected.
    @Test
    void shouldRejectPublishWithUnsupportedQos() {
        ClientConnection publisher = connectClient("publisher-qos1", 5);

        PublishResult result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                1,
                false,
                false,
                "payload".getBytes()));

        assertFalse(result.accepted());
        assertTrue(result.deliveries().isEmpty());
        assertEquals(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED, result.disconnectReasonCode());
    }

    // Verifies that an explicit disconnect unbinds the current session from its live connection.
    @Test
    void shouldUnbindSessionWhenClientDisconnects() {
        ClientConnection connection = connectClient("disconnect-client", 5);

        protocolEngine.handleDisconnect(connection);

        assertEquals(ConnectionState.DISCONNECTING, connection.state());
        assertNull(sessionRegistry.find("disconnect-client").orElseThrow().connectionId());
    }

    // Verifies that closing a superseded connection does not accidentally unbind the newer takeover session.
    @Test
    void shouldKeepNewSessionBindingWhenSupersededConnectionCloses() {
        ClientConnection firstConnection = connectClient("takeover-client", 5);
        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "takeover-client", "MQTT", 5, true);

        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, new ConnectRequest(
                "takeover-client",
                "MQTT",
                5,
                true,
                null,
                false));
        assertTrue(secondDecision.accepted());

        protocolEngine.handleConnectionClosed(firstConnection);

        assertEquals(ConnectionState.CLOSED, firstConnection.state());
        assertEquals(secondConnection.internalId(),
                sessionRegistry.find("takeover-client").orElseThrow().connectionId());
    }

    private ClientConnection connectClient(String clientId, int protocolVersion) {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", clientId, "MQTT", protocolVersion, true);
        ConnectDecision decision = protocolEngine.handleConnect(connection, new ConnectRequest(
                clientId,
                "MQTT",
                protocolVersion,
                true,
                null,
                false));
        assertTrue(decision.accepted());
        return connection;
    }

    /**
     * Test double used to keep assertions focused on protocol outcomes.
     */
    private static final class NoOpBrokerEventSink implements BrokerEventSink {

        @Override
        public void transportStarted(String host, int port) {
        }

        @Override
        public void transportStopped() {
        }

        @Override
        public void connectionAccepted(ClientConnection connection) {
        }

        @Override
        public void subscriptionAdded(ClientConnection connection, String topicFilter) {
        }

        @Override
        public void subscriptionRemoved(ClientConnection connection, String topicFilter) {
        }

        @Override
        public void messageRouted(ClientConnection connection, String topicName, int matchedClients) {
        }

        @Override
        public void protocolWarning(ClientConnection connection, String message) {
        }
    }
}
