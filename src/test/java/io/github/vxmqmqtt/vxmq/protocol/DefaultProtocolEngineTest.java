package io.github.vxmqmqtt.vxmq.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.authn.AuthnProvider;
import io.github.vxmqmqtt.vxmq.authn.AuthnResult;
import io.github.vxmqmqtt.vxmq.authn.PermitAllAuthnProvider;
import io.github.vxmqmqtt.vxmq.authz.AuthzAction;
import io.github.vxmqmqtt.vxmq.authz.AuthzChain;
import io.github.vxmqmqtt.vxmq.authz.AuthzNoMatchPolicy;
import io.github.vxmqmqtt.vxmq.authz.AuthzProvider;
import io.github.vxmqmqtt.vxmq.authz.AuthzReason;
import io.github.vxmqmqtt.vxmq.authz.AuthzResult;
import io.github.vxmqmqtt.vxmq.authz.AuthzDefinition;
import io.github.vxmqmqtt.vxmq.authz.AuthzAuthorizer;
import io.github.vxmqmqtt.vxmq.authz.ConfiguredAuthzProvider;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.model.AcceptedConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.DeliveryPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPubRelOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPublishOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.MessageExpiry;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttUserProperty;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttUserProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgementType;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt311ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.Mqtt5ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.OutboundPubRecOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishReleaseDisposition;
import io.github.vxmqmqtt.vxmq.protocol.model.RejectedConnectResponse;
import io.github.vxmqmqtt.vxmq.protocol.model.ReplayPublish;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumePlan;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItem;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeAck;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.retained.InMemoryRetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.session.InMemorySessionRegistry;
import io.github.vxmqmqtt.vxmq.session.SessionOpenRequest;
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
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for protocol decisions without starting the transport layer.
 */
class DefaultProtocolEngineTest {

    private ClientConnectionRegistry connectionRegistry;
    private SessionRegistry sessionRegistry;
    private RetainedMessageRegistry retainedMessageRegistry;
    private SubscriptionRegistry subscriptionRegistry;
    private DefaultMqttTopicSupport mqttTopicSupport;
    private DefaultProtocolEngine protocolEngine;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        mqttTopicSupport = new DefaultMqttTopicSupport();
        connectionRegistry = new ClientConnectionRegistry();
        sessionRegistry = new InMemorySessionRegistry();
        retainedMessageRegistry = new InMemoryRetainedMessageRegistry(mqttTopicSupport);
        subscriptionRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        clock = new MutableClock(Instant.parse("2026-04-30T00:00:00Z"));
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock);
    }

    // Verifies that MQTT 3.1.1 rejects empty client ids when the session is not clean.
    @Test
    void shouldRejectEmptyClientIdForPersistentMqtt311Session() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 4, false);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, mqtt311Connect(
                "",
                false));

        RejectedConnectResponse response = (RejectedConnectResponse) decision.response();
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, response.returnCode());
    }

    // Verifies that MQTT 3.1.1 clean sessions can receive an auto-generated client id.
    @Test
    void shouldAssignClientIdForMqtt311CleanSession() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 4, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, mqtt311Connect(
                "",
                true));

        AcceptedConnectResponse response = (AcceptedConnectResponse) decision.response();
        assertNotNull(response.effectiveClientId());
        assertTrue(response.effectiveClientId().startsWith("vxmq-"));
        assertTrue(response.responseProperties().isEmpty());
        assertFalse(response.sessionPresent());
    }

    // Verifies that MQTT 5 returns Assigned Client Identifier when the broker generates the id.
    @Test
    void shouldAssignClientIdAndConnAckPropertyForMqtt5() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, mqtt5Connect(
                "",
                true,
                0L));

        AcceptedConnectResponse response = (AcceptedConnectResponse) decision.response();
        assertNotNull(response.effectiveClientId());
        MqttProperties.MqttProperty<?> assignedClientIdProperty = response.responseProperties()
                .getProperty(MqttProperties.MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value());
        assertNotNull(assignedClientIdProperty);
        assertEquals(response.effectiveClientId(), assignedClientIdProperty.value());
    }

    // Verifies that MQTT 5 CONNACK declares the broker Receive Maximum.
    @Test
    void shouldDeclareReceiveMaximumForMqtt5() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "client-rm", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, mqtt5Connect(
                "client-rm",
                true,
                0L));

        AcceptedConnectResponse response = (AcceptedConnectResponse) decision.response();
        MqttProperties.MqttProperty<?> property = response.responseProperties()
                .getProperty(MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM.value());
        assertNotNull(property);
        assertEquals(65_535, ((Number) property.value()).intValue());
    }

    // Verifies that MQTT 5 CONNACK declares the broker Maximum Packet Size.
    @Test
    void shouldDeclareMaximumPacketSizeForMqtt5() {
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock,
                65_535,
                256);
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "client-max-packet", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, mqtt5Connect(
                "client-max-packet",
                true,
                0L));

        AcceptedConnectResponse response = (AcceptedConnectResponse) decision.response();
        MqttProperties.MqttProperty<?> property = response.responseProperties()
                .getProperty(MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE.value());
        assertNotNull(property);
        assertEquals(256, ((Number) property.value()).intValue());
    }

    // Verifies that a second connection with the same client id marks the previous one as superseded.
    @Test
    void shouldMarkPreviousConnectionForTakeOver() {
        ClientConnection firstConnection = connectionRegistry.open("127.0.0.1", "client-a", "MQTT", 5, true);
        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "client-a", "MQTT", 5, true);

        ConnectOutcome firstDecision = protocolEngine.handleConnect(firstConnection, mqtt5Connect(
                "client-a",
                true,
                0L));
        ConnectOutcome secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "client-a",
                true,
                0L));

        assertFalse(firstDecision.takeoverPlan().requiresTakeover());
        assertEquals(firstConnection.connectionId(), secondDecision.takeoverPlan().supersededConnectionId());
    }

    // Verifies that MQTT 3.1.1 cleanSession=false restores an existing persistent session.
    @Test
    void shouldRestorePersistentMqtt311SessionOnReconnect() {
        ClientConnection firstConnection = connectionRegistry.open("127.0.0.1", "mqtt311-persistent", "MQTT", 4, false);
        ConnectOutcome firstDecision = protocolEngine.handleConnect(firstConnection, mqtt311Connect(
                "mqtt311-persistent",
                false));
        assertFalse(((AcceptedConnectResponse) firstDecision.response()).sessionPresent());

        protocolEngine.handleSubscribe(firstConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        closeClientConnection(firstConnection);

        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "mqtt311-persistent", "MQTT", 4, false);
        ConnectOutcome secondDecision = protocolEngine.handleConnect(secondConnection, mqtt311Connect(
                "mqtt311-persistent",
                false));

        assertTrue(((AcceptedConnectResponse) secondDecision.response()).sessionPresent());
        assertTrue(sessionRegistry.find("mqtt311-persistent").orElseThrow().subscriptions().contains("sensors/+/temperature"));
    }

    // Verifies that MQTT 3.1.1 cleanSession=true always starts with a fresh session.
    @Test
    void shouldDiscardExistingSessionForCleanMqtt311Reconnect() {
        ClientConnection firstConnection = connectClient("mqtt311-clean", 4, true, false, null);
        protocolEngine.handleSubscribe(firstConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        closeClientConnection(firstConnection);

        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "mqtt311-clean", "MQTT", 4, true);
        ConnectOutcome secondDecision = protocolEngine.handleConnect(secondConnection, mqtt311Connect(
                "mqtt311-clean",
                true));

        assertFalse(((AcceptedConnectResponse) secondDecision.response()).sessionPresent());
        assertTrue(sessionRegistry.find("mqtt311-clean").orElseThrow().subscriptions().isEmpty());
    }

    // Verifies that MQTT 5 cleanStart=false restores the existing session when one is present.
    @Test
    void shouldRestoreMqtt5SessionWhenCleanStartIsFalse() {
        ClientConnection firstConnection = connectClient("mqtt5-restored", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        closeClientConnection(firstConnection);

        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "mqtt5-restored", "MQTT", 5, false);
        ConnectOutcome secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "mqtt5-restored",
                false,
                60L));

        assertTrue(((AcceptedConnectResponse) secondDecision.response()).sessionPresent());
        assertTrue(sessionRegistry.find("mqtt5-restored").orElseThrow().subscriptions().contains("sensors/+/temperature"));
    }

    // Verifies that MQTT 5 cleanStart=true discards any previous session state before reconnecting.
    @Test
    void shouldDiscardMqtt5SessionWhenCleanStartIsTrue() {
        ClientConnection firstConnection = connectClient("mqtt5-fresh", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        protocolEngine.handleConnectionClosed(firstConnection);

        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "mqtt5-fresh", "MQTT", 5, true);
        ConnectOutcome secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "mqtt5-fresh",
                true,
                60L));

        assertFalse(((AcceptedConnectResponse) secondDecision.response()).sessionPresent());
        assertTrue(sessionRegistry.find("mqtt5-fresh").orElseThrow().subscriptions().isEmpty());
    }

    // Verifies that MQTT 5 session expiry 0 destroys the session once the connection closes.
    @Test
    void shouldDeleteMqtt5SessionWhenExpiryIsZero() {
        ClientConnection connection = connectClient("mqtt5-ephemeral", 5, false, false, 0L);
        protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        closeClientConnection(connection);

        assertTrue(sessionRegistry.find("mqtt5-ephemeral").isEmpty());
    }

    // Verifies that subscriptions requesting QoS 2 are granted exactly once delivery.
    @Test
    void shouldGrantQos2ForSupportedSubscription() {
        ClientConnection connection = connectClient("client-sub", 5, true, false, 0L);

        SubscribeOutcome result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        assertEquals(1, result.ack().itemResults().size());
        assertEquals(MqttQoS.EXACTLY_ONCE, result.ack().itemResults().getFirst().grantedQos());
        assertEquals(MqttSubAckReasonCode.GRANTED_QOS2, result.ack().itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-sub").orElseThrow().subscriptions().contains("sensors/+/temperature"));
        assertEquals(1, subscriptionRegistry.match("sensors/room-1/temperature").size());
    }

    // Verifies that invalid topic filters are rejected without mutating session state.
    @Test
    void shouldRejectInvalidSubscriptionFilter() {
        ClientConnection connection = connectClient("client-invalid-sub", 5, true, false, 0L);

        SubscribeOutcome result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/#/temperature", 0))));

        assertEquals(1, result.ack().itemResults().size());
        assertEquals(MqttSubAckReasonCode.TOPIC_FILTER_INVALID, result.ack().itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-invalid-sub").orElseThrow().subscriptions().isEmpty());
    }

    // Verifies that subscription authorization rejects without mutating session or routing state.
    @Test
    void shouldRejectUnauthorizedSubscriptionWithoutMutatingState() {
        ClientConnection connection = connectClient("client-sub-denied", 5, true, false, 0L);
        protocolEngine = protocolEngineWithAuthz(context ->
                context.action() == AuthzAction.SUBSCRIBE
                        ? AuthzResult.deny(AuthzReason.NOT_AUTHORIZED)
                        : AuthzResult.allow());

        SubscribeOutcome result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        assertEquals(MqttSubAckReasonCode.NOT_AUTHORIZED, result.ack().itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-sub-denied").orElseThrow().subscriptions().isEmpty());
        assertTrue(subscriptionRegistry.match("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that a failed routing write for a new subscription does not leave session-only state behind.
    @Test
    void shouldRollbackNewSessionSubscriptionWhenRoutingAddFails() {
        ClientConnection connection = connectClient("client-sub-add-failure", 5, true, false, 0L);
        protocolEngine = protocolEngineWith(new FailingSubscriptionRegistry(subscriptionRegistry, true, false));

        SubscribeOutcome result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));

        assertEquals(MqttSubAckReasonCode.UNSPECIFIED_ERROR, result.ack().itemResults().getFirst().reasonCode());
        assertNull(sessionRegistry.find("client-sub-add-failure").orElseThrow().subscription("sensors/+/temperature"));
    }

    // Verifies that a failed routing write while replacing a subscription restores the original session binding.
    @Test
    void shouldRestorePreviousSessionSubscriptionWhenRoutingReplacementFails() {
        ClientConnection connection = connectClient("client-sub-replace-failure", 5, true, false, 0L);
        protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        SubscriptionBinding original = sessionRegistry.find("client-sub-replace-failure")
                .orElseThrow()
                .subscription("sensors/+/temperature");
        protocolEngine = protocolEngineWith(new FailingSubscriptionRegistry(subscriptionRegistry, true, false));

        SubscribeOutcome result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        2,
                        true,
                        true,
                        RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE,
                        42))));

        SubscriptionBinding restored = sessionRegistry.find("client-sub-replace-failure")
                .orElseThrow()
                .subscription("sensors/+/temperature");
        assertEquals(MqttSubAckReasonCode.UNSPECIFIED_ERROR, result.ack().itemResults().getFirst().reasonCode());
        assertEquals(original, restored);
    }

    // Verifies that unsubscribe removes state from both the session registry and routing registry.
    @Test
    void shouldRemoveExistingSubscriptionOnUnsubscribe() {
        ClientConnection connection = connectClient("client-unsub", 5, true, false, 0L);
        protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        UnsubscribeAck result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/+/temperature")));

        assertEquals(1, result.itemResults().size());
        assertEquals(MqttUnsubAckReasonCode.SUCCESS, result.itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-unsub").orElseThrow().subscriptions().isEmpty());
        assertTrue(subscriptionRegistry.match("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that unsubscribing an unknown filter returns a non-error MQTT 5 reason code.
    @Test
    void shouldReportNoSubscriptionExistedOnUnsubscribe() {
        ClientConnection connection = connectClient("client-unsub-missing", 5, true, false, 0L);

        UnsubscribeAck result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/+/temperature")));

        assertEquals(1, result.itemResults().size());
        assertEquals(MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED, result.itemResults().getFirst().reasonCode());
    }

    // Verifies that a failed routing delete restores the session subscription so both views can be retried.
    @Test
    void shouldRestoreSessionSubscriptionWhenRoutingRemoveFails() {
        ClientConnection connection = connectClient("client-unsub-remove-failure", 5, true, false, 0L);
        protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));
        SubscriptionBinding original = sessionRegistry.find("client-unsub-remove-failure")
                .orElseThrow()
                .subscription("sensors/+/temperature");
        protocolEngine = protocolEngineWith(new FailingSubscriptionRegistry(subscriptionRegistry, false, true));

        UnsubscribeAck result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/+/temperature")));

        assertEquals(MqttUnsubAckReasonCode.UNSPECIFIED_ERROR, result.itemResults().getFirst().reasonCode());
        assertEquals(original, sessionRegistry.find("client-unsub-remove-failure")
                .orElseThrow()
                .subscription("sensors/+/temperature"));
    }

    // Verifies that invalid filters are rejected during unsubscribe as well.
    @Test
    void shouldRejectInvalidUnsubscribeFilter() {
        ClientConnection connection = connectClient("client-unsub-invalid", 5, true, false, 0L);

        UnsubscribeAck result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/#/temperature")));

        assertEquals(1, result.itemResults().size());
        assertEquals(MqttUnsubAckReasonCode.TOPIC_FILTER_INVALID, result.itemResults().getFirst().reasonCode());
    }

    // Verifies that a published message is routed to the matching subscriber set.
    @Test
    void shouldRoutePublishToMatchedSubscribers() {
        ClientConnection publisher = connectClient("publisher", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes()));

        assertFalse(result.disconnectAction().isDisconnect());
        assertEquals(PublishAcknowledgementType.NONE, result.acknowledgement().type());
        assertEquals(1, result.deliveryPlan().deliveries().size());
        assertEquals("subscriber", result.deliveryPlan().deliveries().getFirst().clientId());
        assertEquals(MqttQoS.AT_MOST_ONCE, result.deliveryPlan().deliveries().getFirst().grantedQos());
    }

    // Verifies that publish authorization rejects QoS 1 without routing to subscribers.
    @Test
    void shouldRejectUnauthorizedQos1PublishWithoutRouting() {
        ClientConnection publisher = connectClient("publisher-denied", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-denied", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));
        protocolEngine = protocolEngineWithAuthz(context ->
                context.action() == AuthzAction.PUBLISH
                        ? AuthzResult.deny(AuthzReason.NOT_AUTHORIZED)
                        : AuthzResult.allow());

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                7,
                1,
                false,
                false,
                "payload".getBytes()));

        assertEquals(PublishAcknowledgementType.PUBACK, result.acknowledgement().type());
        assertEquals(MqttPubAckReasonCode.NOT_AUTHORIZED, result.acknowledgement().mqtt5ReasonCode());
        assertTrue(result.deliveryPlan().deliveries().isEmpty());
    }

    // Verifies that publish authorization rejects QoS 2 before creating inbound state.
    @Test
    void shouldRejectUnauthorizedQos2PublishWithoutCreatingInboundState() {
        ClientConnection publisher = connectClient("publisher-qos2-denied", 5, true, false, 0L);
        protocolEngine = protocolEngineWithAuthz(context ->
                context.action() == AuthzAction.PUBLISH
                        ? AuthzResult.deny(AuthzReason.NOT_AUTHORIZED)
                        : AuthzResult.allow());

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                8,
                2,
                false,
                false,
                "payload".getBytes()));

        assertEquals(PublishAcknowledgementType.PUBREC, result.acknowledgement().type());
        assertEquals(MqttPubRecReasonCode.NOT_AUTHORIZED, result.acknowledgement().mqtt5ReasonCode());
        assertEquals(0, sessionRegistry.find("publisher-qos2-denied").orElseThrow().inboundQos2MessageCount());
    }

    // Verifies that online deliveries preserve MQTT 5 PUBLISH User Property order and duplicate keys.
    @Test
    void shouldPreserveUserPropertiesForOnlinePublishDelivery() {
        ClientConnection publisher = connectClient("publisher-user-properties", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-user-properties", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes(),
                userProperties(
                        new MqttUserProperty("trace", "a"),
                        new MqttUserProperty("trace", "b"))));

        assertEquals(1, result.deliveryPlan().deliveries().size());
        assertEquals(
                List.of(new MqttUserProperty("trace", "a"), new MqttUserProperty("trace", "b")),
                result.deliveryPlan().deliveries().getFirst().properties().userProperties().values());
    }

    // Verifies that expired MQTT 5 publishes are acknowledged but not routed to online subscribers.
    @Test
    void shouldDropExpiredOnlinePublishDelivery() {
        ClientConnection publisher = connectClient("publisher-expired-online", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-expired-online", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes(),
                messageExpiryAt(clock.instant().minusSeconds(1))));

        assertFalse(result.disconnectAction().isDisconnect());
        assertTrue(result.deliveryPlan().deliveries().isEmpty());
        assertEquals(0, result.deliveryPlan().queuedMessageCount());
    }

    // Verifies that live deliveries preserve the publish expiry snapshot for outbound property writing.
    @Test
    void shouldPreserveMessageExpiryForOnlinePublishDelivery() {
        ClientConnection publisher = connectClient("publisher-expiry-online", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-expiry-online", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        PublishProperties properties = messageExpiryAt(clock.instant().plusSeconds(60));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes(),
                properties));

        assertEquals(properties.messageExpiry(), result.deliveryPlan().deliveries().getFirst().properties().messageExpiry());
    }

    // Verifies that online deliveries preserve MQTT 5 request-response properties.
    @Test
    void shouldPreserveRequestResponsePropertiesForOnlinePublishDelivery() {
        ClientConnection publisher = connectClient("publisher-request-response", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-request-response", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("requests/+", 0))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "requests/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes(),
                requestResponseProperties("responses/client-a", new byte[]{1, 2, 3})));

        PublishProperties properties = result.deliveryPlan().deliveries().getFirst().properties();
        assertEquals("responses/client-a", properties.responseTopic());
        assertArrayEquals(new byte[]{1, 2, 3}, properties.correlationData());
    }

    // Verifies that invalid Response Topic values are rejected before routing state changes.
    @Test
    void shouldRejectPublishWithInvalidResponseTopic() {
        ClientConnection publisher = connectClient("publisher-invalid-response-topic", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-invalid-response-topic", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("requests/+", 0))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "requests/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes(),
                requestResponseProperties("responses/+", new byte[]{1, 2, 3})));

        assertTrue(result.disconnectAction().isDisconnect());
        assertEquals(MqttDisconnectReasonCode.PROTOCOL_ERROR, result.disconnectAction().reasonCode());
        assertTrue(result.deliveryPlan().isEmpty());
    }

    // Verifies that duplicate singleton request-response properties are rejected as protocol errors.
    @Test
    void shouldRejectPublishWithDuplicateRequestResponseProperties() {
        ClientConnection publisher = connectClient("publisher-duplicate-request-response", 5, true, false, 0L);

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "requests/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes(),
                new PublishProperties(
                        MqttUserProperties.empty(),
                        MessageExpiry.none(),
                        "responses/client-a",
                        new byte[]{1, 2, 3},
                        true,
                        true)));

        assertTrue(result.disconnectAction().isDisconnect());
        assertEquals(MqttDisconnectReasonCode.PROTOCOL_ERROR, result.disconnectAction().reasonCode());
    }

    // Verifies that MQTT 5 No Local subscriptions do not receive publishes from the same client.
    @Test
    void shouldSkipNoLocalDeliveryForSameClientPublisher() {
        ClientConnection client = connectClient("client-no-local", 5, true, false, 0L);
        protocolEngine.handleSubscribe(client, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        0,
                        true,
                        false,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                        null))));

        InboundPublishOutcome result = protocolEngine.handlePublish(client, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes()));

        assertTrue(result.deliveryPlan().deliveries().isEmpty());
        assertEquals(0, result.deliveryPlan().queuedMessageCount());
    }

    // Verifies that Retain As Published controls the retain flag on live deliveries.
    @Test
    void shouldApplyRetainAsPublishedToLiveDeliveryRetainFlag() {
        ClientConnection publisher = connectClient("publisher-rap-live", 5, true, false, 0L);
        ClientConnection strippingSubscriber = connectClient("subscriber-rap-strip", 5, true, false, 0L);
        ClientConnection preservingSubscriber = connectClient("subscriber-rap-preserve", 5, true, false, 0L);
        protocolEngine.handleSubscribe(strippingSubscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        0,
                        false,
                        false,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                        null))));
        protocolEngine.handleSubscribe(preservingSubscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        0,
                        false,
                        true,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                        null))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                true,
                false,
                "payload".getBytes()));

        assertEquals(2, result.deliveryPlan().deliveries().size());
        PublishDelivery stripped = deliveryFor(result.deliveryPlan().deliveries(), "subscriber-rap-strip");
        PublishDelivery preserved = deliveryFor(result.deliveryPlan().deliveries(), "subscriber-rap-preserve");
        assertFalse(stripped.retain());
        assertTrue(preserved.retain());
    }

    // Verifies that online QoS 1 subscribers receive an inflight delivery with a packet id and a PUBACK requirement.
    @Test
    void shouldCreateInflightQos1DeliveryForOnlineSubscriber() {
        ClientConnection publisher = connectClient("publisher-qos1-online", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-qos1-online", 5, false, false, 60L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                21,
                1,
                false,
                false,
                "payload".getBytes()));

        assertFalse(result.disconnectAction().isDisconnect());
        assertEquals(PublishAcknowledgementType.PUBACK, result.acknowledgement().type());
        assertEquals(MqttPubAckReasonCode.SUCCESS, result.acknowledgement().mqtt5ReasonCode());
        assertEquals(1, result.deliveryPlan().deliveries().size());
        assertEquals(MqttQoS.AT_LEAST_ONCE, result.deliveryPlan().deliveries().getFirst().grantedQos());
        assertNotNull(result.deliveryPlan().deliveries().getFirst().packetId());
        assertEquals(1, sessionRegistry.find("subscriber-qos1-online").orElseThrow().inflightMessageCount());

        protocolEngine.handlePubAck(subscriber, result.deliveryPlan().deliveries().getFirst().packetId());

        assertEquals(0, sessionRegistry.find("subscriber-qos1-online").orElseThrow().inflightMessageCount());
    }

    // Verifies that outbound QoS 1 delivery honors the subscriber Receive Maximum and drains after PUBACK.
    @Test
    void shouldQueueOutboundQos1WhenSubscriberReceiveMaximumIsFull() {
        ClientConnection publisher = connectClient("publisher-rm-qos1", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-rm-qos1", 5, false, false, 60L, null, 1);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));

        InboundPublishOutcome first = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                41,
                1,
                false,
                false,
                "first".getBytes()));
        InboundPublishOutcome second = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                42,
                1,
                false,
                false,
                "second".getBytes()));

        assertEquals(1, first.deliveryPlan().deliveries().size());
        assertTrue(second.deliveryPlan().deliveries().isEmpty());
        assertEquals(1, sessionRegistry.find("subscriber-rm-qos1").orElseThrow().inflightMessageCount());

        DeliveryPlan drained = protocolEngine.handlePubAck(
                subscriber,
                first.deliveryPlan().deliveries().getFirst().packetId());

        assertEquals(1, drained.deliveries().size());
        assertEquals("second", new String(drained.deliveries().getFirst().payloadCopy()));
        assertEquals(1, sessionRegistry.find("subscriber-rm-qos1").orElseThrow().inflightMessageCount());
    }

    // Verifies that inbound MQTT 5 PUBLISH packets over the broker Maximum Packet Size are rejected.
    @Test
    void shouldDisconnectWhenInboundPublishExceedsMaximumPacketSize() {
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock,
                65_535,
                16);
        ClientConnection publisher = connectClient("publisher-max-packet-inbound", 5, true, false, 0L);

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                61,
                1,
                false,
                false,
                "payload".getBytes(),
                PublishProperties.empty(),
                32));

        assertTrue(result.disconnectAction().isDisconnect());
        assertEquals(MqttDisconnectReasonCode.PACKET_TOO_LARGE, result.disconnectAction().reasonCode());
    }

    // Verifies that outbound publishes over the subscriber Maximum Packet Size are skipped without inflight state.
    @Test
    void shouldSkipOutboundPublishWhenSubscriberMaximumPacketSizeIsExceeded() {
        ClientConnection publisher = connectClient("publisher-max-packet-outbound", 5, true, false, 0L);
        ClientConnection subscriber = connectClient(
                "subscriber-max-packet-outbound",
                5,
                false,
                false,
                60L,
                null,
                65_535,
                16);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                62,
                1,
                false,
                false,
                "payload".getBytes()));

        assertFalse(result.disconnectAction().isDisconnect());
        assertTrue(result.deliveryPlan().deliveries().isEmpty());
        assertEquals(0, sessionRegistry.find("subscriber-max-packet-outbound").orElseThrow().inflightMessageCount());
    }

    // Verifies that absent client CONNECT limits are defaulted only when opening the session.
    @Test
    void shouldDefaultAbsentMqtt5ConnectLimitsWhenOpeningSession() {
        AtomicReference<SessionOpenRequest> capturedRequest = new AtomicReference<>();
        sessionRegistry = new InMemorySessionRegistry() {
            @Override
            public io.github.vxmqmqtt.vxmq.session.SessionOpenResult openSession(
                    String clientId,
                    SessionOpenRequest request) {
                capturedRequest.set(request);
                return super.openSession(clientId, request);
            }
        };
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock);
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "client-default-limits", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, new Mqtt5ConnectRequest(
                "client-default-limits",
                "MQTT",
                true,
                0L,
                null,
                false,
                null,
                new ConnectProperties(MqttUserProperties.empty(), null, null)));

        assertTrue(decision.response() instanceof AcceptedConnectResponse);
        assertNotNull(capturedRequest.get());
        assertEquals(
                ConnectProperties.DEFAULT_RECEIVE_MAXIMUM,
                capturedRequest.get().receiveMaximum());
        assertEquals(
                ConnectProperties.DEFAULT_MAXIMUM_PACKET_SIZE,
                capturedRequest.get().maximumPacketSize());
    }

    // Verifies that zero-valued MQTT 5 CONNECT limits are rejected as protocol errors.
    @Test
    void shouldRejectZeroMqtt5ConnectLimitsAsProtocolError() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "client-zero-limits", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, new Mqtt5ConnectRequest(
                "client-zero-limits",
                "MQTT",
                true,
                0L,
                null,
                false,
                null,
                new ConnectProperties(MqttUserProperties.empty(), 0, 0)));

        RejectedConnectResponse response = (RejectedConnectResponse) decision.response();
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR, response.returnCode());
        assertTrue(sessionRegistry.find("client-zero-limits").isEmpty());
    }

    // Verifies that out-of-range MQTT 5 CONNECT limits are rejected as protocol errors.
    @Test
    void shouldRejectOutOfRangeMqtt5ConnectLimitsAsProtocolError() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "client-bad-limits", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, new Mqtt5ConnectRequest(
                "client-bad-limits",
                "MQTT",
                true,
                0L,
                null,
                false,
                null,
                new ConnectProperties(MqttUserProperties.empty(), -1, 268_435_456)));

        RejectedConnectResponse response = (RejectedConnectResponse) decision.response();
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR, response.returnCode());
        assertTrue(sessionRegistry.find("client-bad-limits").isEmpty());
    }

    // Verifies that absent MQTT 5 Session Expiry is defaulted only when opening the session.
    @Test
    void shouldDefaultAbsentMqtt5SessionExpiryWhenOpeningSession() {
        AtomicReference<SessionOpenRequest> capturedRequest = new AtomicReference<>();
        sessionRegistry = new InMemorySessionRegistry() {
            @Override
            public io.github.vxmqmqtt.vxmq.session.SessionOpenResult openSession(
                    String clientId,
                    SessionOpenRequest request) {
                capturedRequest.set(request);
                return super.openSession(clientId, request);
            }
        };
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock);
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "client-default-expiry", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, new Mqtt5ConnectRequest(
                "client-default-expiry",
                "MQTT",
                true,
                null,
                null,
                false,
                null,
                ConnectProperties.empty()));

        assertTrue(decision.response() instanceof AcceptedConnectResponse);
        assertNotNull(capturedRequest.get());
        assertEquals(0L, capturedRequest.get().sessionExpiryIntervalSeconds());
    }

    // Verifies that the MQTT 5 maximum Session Expiry Interval is kept as a positive long.
    @Test
    void shouldKeepSessionWithMaximumMqtt5SessionExpiryAfterClose() {
        ClientConnection connection = connectClient("client-max-expiry", 5, false, false, 0xFFFF_FFFFL);

        protocolEngine.handleConnectionClosed(connection);

        assertTrue(sessionRegistry.find("client-max-expiry").isPresent());
        assertNull(sessionRegistry.find("client-max-expiry").orElseThrow().connectionId());
        assertEquals(0xFFFF_FFFFL,
                sessionRegistry.find("client-max-expiry").orElseThrow().sessionExpiryIntervalSeconds());
    }

    // Verifies that MQTT 5 SUBSCRIBE Subscription Identifier 0 is a protocol error.
    @Test
    void shouldDisconnectOnZeroSubscriptionIdentifier() {
        ClientConnection connection = connectClient("subscriber-zero-identifier", 5, true, false, 0L);

        SubscribeOutcome outcome = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(
                List.of(new SubscriptionItem("sensors/+/temperature", 1)),
                new SubscriptionProperties(MqttUserProperties.empty(), 0)));

        assertTrue(outcome.disconnectAction().isDisconnect());
        assertEquals(MqttDisconnectReasonCode.PROTOCOL_ERROR, outcome.disconnectAction().reasonCode());
        assertTrue(subscriptionRegistry.match("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that duplicated MQTT 5 SUBSCRIBE Subscription Identifier is a protocol error.
    @Test
    void shouldDisconnectOnDuplicatedSubscriptionIdentifier() {
        ClientConnection connection = connectClient("subscriber-duplicate-identifier", 5, true, false, 0L);

        SubscribeOutcome outcome = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(
                List.of(new SubscriptionItem("sensors/+/temperature", 1)),
                new SubscriptionProperties(MqttUserProperties.empty(), 42, true)));

        assertTrue(outcome.disconnectAction().isDisconnect());
        assertEquals(MqttDisconnectReasonCode.PROTOCOL_ERROR, outcome.disconnectAction().reasonCode());
        assertTrue(subscriptionRegistry.match("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that inbound QoS 2 Receive Maximum is enforced for different packet ids.
    @Test
    void shouldDisconnectWhenInboundQos2ReceiveMaximumIsExceeded() {
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock,
                1);
        ClientConnection publisher = connectClient("publisher-rm-inbound", 5, true, false, 0L);

        InboundPublishOutcome first = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                51,
                2,
                false,
                false,
                "first".getBytes()));
        InboundPublishOutcome duplicate = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                51,
                2,
                false,
                true,
                "duplicate".getBytes()));
        InboundPublishOutcome second = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                52,
                2,
                false,
                false,
                "second".getBytes()));

        assertFalse(first.disconnectAction().isDisconnect());
        assertFalse(duplicate.disconnectAction().isDisconnect());
        assertTrue(second.disconnectAction().isDisconnect());
        assertEquals(MqttDisconnectReasonCode.RECEIVE_MAXIMUM_EXCEEDED, second.disconnectAction().reasonCode());
    }

    // Verifies that retained publishes are stored and replayed immediately after a matching subscribe.
    @Test
    void shouldReplayRetainedMessageAfterSubscribe() {
        ClientConnection publisher = connectClient("publisher-retained", 5, true, false, 0L);

        InboundPublishOutcome publishResult = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                true,
                false,
                "retained-payload".getBytes()));

        assertFalse(publishResult.disconnectAction().isDisconnect());
        assertTrue(retainedMessageRegistry.findExact("sensors/room-1/temperature").isPresent());

        ClientConnection subscriber = connectClient("subscriber-retained", 5, true, false, 0L);
        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        assertEquals(1, subscribeResult.retainedReplayPlan().deliveries().size());
        PublishDelivery retainedDelivery = subscribeResult.retainedReplayPlan().deliveries().getFirst();
        assertEquals("subscriber-retained", retainedDelivery.clientId());
        assertEquals("sensors/room-1/temperature", retainedDelivery.topicName());
        assertEquals(MqttQoS.AT_MOST_ONCE, retainedDelivery.grantedQos());
        assertTrue(retainedDelivery.retain());
    }

    // Verifies that Retain Handling can suppress replay when a subscription already exists.
    @Test
    void shouldReplayRetainedMessageOnlyForNewSubscriptionWhenRequested() {
        ClientConnection publisher = connectClient("publisher-retain-handling-existing", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                true,
                false,
                "retained-payload".getBytes()));
        ClientConnection subscriber = connectClient("subscriber-retain-handling-existing", 5, true, false, 0L);

        SubscribeOutcome firstSubscribe = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        SubscribeOutcome replacementSubscribe = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        0,
                        false,
                        false,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS,
                        null))));

        assertEquals(1, firstSubscribe.retainedReplayPlan().deliveries().size());
        assertTrue(replacementSubscribe.retainedReplayPlan().deliveries().isEmpty());
    }

    // Verifies that Retain Handling can completely suppress retained replay.
    @Test
    void shouldNotReplayRetainedMessageWhenRetainHandlingDisablesIt() {
        ClientConnection publisher = connectClient("publisher-retain-handling-never", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                true,
                false,
                "retained-payload".getBytes()));
        ClientConnection subscriber = connectClient("subscriber-retain-handling-never", 5, true, false, 0L);

        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        0,
                        false,
                        false,
                        RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE,
                        null))));

        assertTrue(subscribeResult.retainedReplayPlan().deliveries().isEmpty());
    }

    // Verifies that retained replay includes the subscription identifier from the matching subscription.
    @Test
    void shouldIncludeSubscriptionIdentifierOnRetainedReplay() {
        ClientConnection publisher = connectClient("publisher-retained-identifier", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                true,
                false,
                "retained-payload".getBytes()));
        ClientConnection subscriber = connectClient("subscriber-retained-identifier", 5, true, false, 0L);

        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        0,
                        false,
                        false,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                        17))));

        PublishDelivery retainedDelivery = subscribeResult.retainedReplayPlan().deliveries().getFirst();
        assertEquals(List.of(17), retainedDelivery.subscriptionIdentifiers());
    }

    // Verifies that retained replay preserves the original PUBLISH User Property list.
    @Test
    void shouldPreserveUserPropertiesForRetainedReplay() {
        ClientConnection publisher = connectClient("publisher-retained-user-properties", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                true,
                false,
                "retained-payload".getBytes(),
                userProperties(new MqttUserProperty("source", "retained"))));
        ClientConnection subscriber = connectClient("subscriber-retained-user-properties", 5, true, false, 0L);

        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        PublishDelivery retainedDelivery = subscribeResult.retainedReplayPlan().deliveries().getFirst();
        assertEquals(
                List.of(new MqttUserProperty("source", "retained")),
                retainedDelivery.properties().userProperties().values());
    }

    // Verifies that retained replay preserves MQTT 5 request-response properties.
    @Test
    void shouldPreserveRequestResponsePropertiesForRetainedReplay() {
        ClientConnection publisher = connectClient("publisher-retained-request-response", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "requests/temperature",
                0,
                0,
                true,
                false,
                "retained-payload".getBytes(),
                requestResponseProperties("responses/client-a", new byte[]{1, 2, 3})));
        ClientConnection subscriber = connectClient("subscriber-retained-request-response", 5, true, false, 0L);

        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("requests/+", 0))));

        PublishProperties properties = subscribeResult.retainedReplayPlan().deliveries().getFirst().properties();
        assertEquals("responses/client-a", properties.responseTopic());
        assertArrayEquals(new byte[]{1, 2, 3}, properties.correlationData());
    }

    // Verifies that expired retained messages are not replayed and are lazily removed.
    @Test
    void shouldNotReplayExpiredRetainedMessage() {
        ClientConnection publisher = connectClient("publisher-retained-expired", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                0,
                0,
                true,
                false,
                "retained-payload".getBytes(),
                messageExpiryAt(clock.instant().plusSeconds(5))));
        assertTrue(retainedMessageRegistry.findExact("sensors/room-1/temperature").isPresent());
        clock.advanceSeconds(6);

        ClientConnection subscriber = connectClient("subscriber-retained-expired", 5, true, false, 0L);
        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        assertTrue(subscribeResult.retainedReplayPlan().deliveries().isEmpty());
        assertTrue(retainedMessageRegistry.findExact("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that retained publishes use the minimum of retained QoS and granted subscription QoS.
    @Test
    void shouldUseMinimumQosForRetainedReplay() {
        ClientConnection publisher = connectClient("publisher-retained-qos1", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                33,
                1,
                true,
                false,
                "retained-qos1".getBytes()));

        ClientConnection subscriber = connectClient("subscriber-retained-qos1", 5, false, false, 60L);
        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));

        assertEquals(1, subscribeResult.retainedReplayPlan().deliveries().size());
        PublishDelivery retainedDelivery = subscribeResult.retainedReplayPlan().deliveries().getFirst();
        assertEquals(MqttQoS.AT_LEAST_ONCE, retainedDelivery.grantedQos());
        assertNotNull(retainedDelivery.packetId());
        assertEquals(1, sessionRegistry.find("subscriber-retained-qos1").orElseThrow().inflightMessageCount());
    }

    // Verifies that retained messages are removed when a retained publish carries an empty payload.
    @Test
    void shouldRemoveRetainedMessageWhenPayloadIsEmpty() {
        ClientConnection publisher = connectClient("publisher-retained-clear", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                34,
                0,
                true,
                false,
                "retained-payload".getBytes()));

        InboundPublishOutcome clearResult = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                35,
                0,
                true,
                false,
                new byte[0]));

        assertFalse(clearResult.disconnectAction().isDisconnect());
        assertTrue(retainedMessageRegistry.findExact("sensors/room-1/temperature").isEmpty());
    }

    // Verifies that QoS 1 publishes for offline persistent subscribers are queued instead of dropped.
    @Test
    void shouldQueueQos1MessageForOfflinePersistentSubscriber() {
        ClientConnection publisher = connectClient("publisher-qos1-offline", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-qos1-offline", 5, false, false, 60L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));
        closeClientConnection(subscriber);

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                22,
                1,
                false,
                false,
                "payload".getBytes()));

        assertFalse(result.disconnectAction().isDisconnect());
        assertEquals(0, result.deliveryPlan().deliveries().size());
        assertEquals(1, result.deliveryPlan().queuedMessageCount());
        assertEquals(1, sessionRegistry.find("subscriber-qos1-offline").orElseThrow().queuedMessageCount());
    }

    // Verifies that queued QoS 1 messages are resumed as inflight deliveries when the subscriber reconnects.
    @Test
    void shouldResumeQueuedQos1MessagesAfterReconnect() {
        ClientConnection publisher = connectClient("publisher-qos1-resume", 5, true, false, 0L);
        ClientConnection firstSubscriberConnection = connectClient("subscriber-qos1-resume", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstSubscriberConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));
        closeClientConnection(firstSubscriberConnection);

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                23,
                1,
                false,
                false,
                "payload".getBytes()));

        ClientConnection secondSubscriberConnection = connectClient("subscriber-qos1-resume", 5, false, false, 60L);
        SessionResumePlan resumePlan = protocolEngine.handleSessionResume(secondSubscriberConnection);
        List<PublishDelivery> resumedDeliveries = resumePlan.actions().stream()
                .map(ReplayPublish.class::cast)
                .map(ReplayPublish::delivery)
                .toList();

        assertEquals(1, resumedDeliveries.size());
        assertTrue(resumedDeliveries.getFirst().fromOfflineQueue());
        assertEquals(MqttQoS.AT_LEAST_ONCE, resumedDeliveries.getFirst().grantedQos());
        assertEquals(1, sessionRegistry.find("subscriber-qos1-resume").orElseThrow().inflightMessageCount());
        assertEquals(0, sessionRegistry.find("subscriber-qos1-resume").orElseThrow().queuedMessageCount());
    }

    // Verifies that offline queued deliveries keep the subscription identifier used at routing time.
    @Test
    void shouldResumeQueuedMessageWithSubscriptionIdentifier() {
        ClientConnection publisher = connectClient("publisher-identifier-resume", 5, true, false, 0L);
        ClientConnection firstSubscriberConnection = connectClient("subscriber-identifier-resume", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstSubscriberConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/+/temperature",
                        1,
                        false,
                        false,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                        21))));
        closeClientConnection(firstSubscriberConnection);

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                23,
                1,
                false,
                false,
                "payload".getBytes()));

        ClientConnection secondSubscriberConnection = connectClient("subscriber-identifier-resume", 5, false, false, 60L);
        SessionResumePlan resumePlan = protocolEngine.handleSessionResume(secondSubscriberConnection);
        List<PublishDelivery> resumedDeliveries = resumePlan.actions().stream()
                .map(ReplayPublish.class::cast)
                .map(ReplayPublish::delivery)
                .toList();

        assertEquals(1, resumedDeliveries.size());
        assertEquals(List.of(21), resumedDeliveries.getFirst().subscriptionIdentifiers());
    }

    // Verifies that offline queued deliveries preserve User Property values across reconnect.
    @Test
    void shouldResumeQueuedMessageWithUserProperties() {
        ClientConnection publisher = connectClient("publisher-user-properties-resume", 5, true, false, 0L);
        ClientConnection firstSubscriberConnection =
                connectClient("subscriber-user-properties-resume", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstSubscriberConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));
        closeClientConnection(firstSubscriberConnection);

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                23,
                1,
                false,
                false,
                "payload".getBytes(),
                userProperties(new MqttUserProperty("offline", "yes"))));

        ClientConnection secondSubscriberConnection =
                connectClient("subscriber-user-properties-resume", 5, false, false, 60L);
        SessionResumePlan resumePlan = protocolEngine.handleSessionResume(secondSubscriberConnection);
        List<PublishDelivery> resumedDeliveries = resumePlan.actions().stream()
                .map(ReplayPublish.class::cast)
                .map(ReplayPublish::delivery)
                .toList();

        assertEquals(1, resumedDeliveries.size());
        assertEquals(
                List.of(new MqttUserProperty("offline", "yes")),
                resumedDeliveries.getFirst().properties().userProperties().values());
    }

    // Verifies that offline queued deliveries preserve MQTT 5 request-response properties across reconnect.
    @Test
    void shouldResumeQueuedMessageWithRequestResponseProperties() {
        ClientConnection publisher = connectClient("publisher-request-response-resume", 5, true, false, 0L);
        ClientConnection firstSubscriberConnection =
                connectClient("subscriber-request-response-resume", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstSubscriberConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("requests/+", 1))));
        closeClientConnection(firstSubscriberConnection);

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "requests/temperature",
                23,
                1,
                false,
                false,
                "payload".getBytes(),
                requestResponseProperties("responses/client-a", new byte[]{1, 2, 3})));

        ClientConnection secondSubscriberConnection =
                connectClient("subscriber-request-response-resume", 5, false, false, 60L);
        SessionResumePlan resumePlan = protocolEngine.handleSessionResume(secondSubscriberConnection);
        List<PublishDelivery> resumedDeliveries = resumePlan.actions().stream()
                .map(ReplayPublish.class::cast)
                .map(ReplayPublish::delivery)
                .toList();

        PublishProperties properties = resumedDeliveries.getFirst().properties();
        assertEquals("responses/client-a", properties.responseTopic());
        assertArrayEquals(new byte[]{1, 2, 3}, properties.correlationData());
    }

    // Verifies that expired queued messages are discarded instead of being resumed after reconnect.
    @Test
    void shouldDropExpiredQueuedMessageOnReconnect() {
        ClientConnection publisher = connectClient("publisher-expired-resume", 5, true, false, 0L);
        ClientConnection firstSubscriberConnection =
                connectClient("subscriber-expired-resume", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstSubscriberConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));
        closeClientConnection(firstSubscriberConnection);

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                23,
                1,
                false,
                false,
                "payload".getBytes(),
                messageExpiryAt(clock.instant().plusSeconds(5))));
        assertEquals(1, sessionRegistry.find("subscriber-expired-resume").orElseThrow().queuedMessageCount());
        clock.advanceSeconds(6);

        ClientConnection secondSubscriberConnection =
                connectClient("subscriber-expired-resume", 5, false, false, 60L);
        SessionResumePlan resumePlan = protocolEngine.handleSessionResume(secondSubscriberConnection);

        assertTrue(resumePlan.actions().isEmpty());
        assertEquals(0, sessionRegistry.find("subscriber-expired-resume").orElseThrow().queuedMessageCount());
        assertEquals(0, sessionRegistry.find("subscriber-expired-resume").orElseThrow().inflightMessageCount());
    }

    // Verifies that inbound QoS 2 publish is routed only after PUBREL and duplicate PUBREL does not re-deliver.
    @Test
    void shouldRouteQos2PublishOnlyAfterPubRel() {
        ClientConnection publisher = connectClient("publisher-qos2-inbound", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-qos2-inbound", 5, false, false, 60L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        InboundPublishOutcome publishResult = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                45,
                2,
                false,
                false,
                "payload-qos2".getBytes()));

        assertFalse(publishResult.disconnectAction().isDisconnect());
        assertEquals(PublishAcknowledgementType.PUBREC, publishResult.acknowledgement().type());
        assertTrue(publishResult.deliveryPlan().isEmpty());
        assertEquals(1, sessionRegistry.find("publisher-qos2-inbound").orElseThrow().inboundQos2MessageCount());

        InboundPubRelOutcome pubRelResult = protocolEngine.handlePubRel(publisher, 45);

        assertEquals(1, pubRelResult.deliveryPlan().deliveries().size());
        assertEquals(MqttQoS.EXACTLY_ONCE, pubRelResult.deliveryPlan().deliveries().getFirst().grantedQos());
        assertEquals(0, sessionRegistry.find("publisher-qos2-inbound").orElseThrow().inboundQos2MessageCount());
        assertEquals(1, sessionRegistry.find("subscriber-qos2-inbound").orElseThrow().inflightMessageCount());
        assertTrue(protocolEngine.handlePubRel(publisher, 45).deliveryPlan().isEmpty());
    }

    // Verifies that QoS 2 delayed routing preserves User Property values saved with the inbound PUBLISH.
    @Test
    void shouldPreserveUserPropertiesForQos2PublishAfterPubRel() {
        ClientConnection publisher = connectClient("publisher-qos2-user-properties", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-qos2-user-properties", 5, false, false, 60L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                49,
                2,
                false,
                false,
                "payload-qos2".getBytes(),
                userProperties(new MqttUserProperty("qos", "2"))));

        InboundPubRelOutcome pubRelResult = protocolEngine.handlePubRel(publisher, 49);

        assertEquals(1, pubRelResult.deliveryPlan().deliveries().size());
        assertEquals(
                List.of(new MqttUserProperty("qos", "2")),
                pubRelResult.deliveryPlan().deliveries().getFirst().properties().userProperties().values());
    }

    // Verifies that QoS 2 delayed routing preserves MQTT 5 request-response properties.
    @Test
    void shouldPreserveRequestResponsePropertiesForQos2PublishAfterPubRel() {
        ClientConnection publisher = connectClient("publisher-qos2-request-response", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-qos2-request-response", 5, false, false, 60L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("requests/+", 2))));

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "requests/temperature",
                49,
                2,
                false,
                false,
                "payload-qos2".getBytes(),
                requestResponseProperties("responses/client-a", new byte[]{1, 2, 3})));

        InboundPubRelOutcome pubRelResult = protocolEngine.handlePubRel(publisher, 49);

        PublishProperties properties = pubRelResult.deliveryPlan().deliveries().getFirst().properties();
        assertEquals("responses/client-a", properties.responseTopic());
        assertArrayEquals(new byte[]{1, 2, 3}, properties.correlationData());
    }

    // Verifies that inbound QoS 2 messages expiring before PUBREL complete without routing.
    @Test
    void shouldDropExpiredQos2PublishAfterPubRel() {
        ClientConnection publisher = connectClient("publisher-qos2-expired", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-qos2-expired", 5, false, false, 60L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                50,
                2,
                false,
                false,
                "payload-qos2".getBytes(),
                messageExpiryAt(clock.instant().plusSeconds(5))));
        clock.advanceSeconds(6);

        InboundPubRelOutcome pubRelResult = protocolEngine.handlePubRel(publisher, 50);

        assertTrue(pubRelResult.deliveryPlan().isEmpty());
        assertEquals(0, sessionRegistry.find("publisher-qos2-expired").orElseThrow().inboundQos2MessageCount());
        assertEquals(0, sessionRegistry.find("subscriber-qos2-expired").orElseThrow().inflightMessageCount());
    }

    // Verifies that outbound QoS 2 advances on PUBREC and clears on PUBCOMP.
    @Test
    void shouldAdvanceOutboundQos2OnPubRecAndPubComp() {
        ClientConnection publisher = connectClient("publisher-qos2-outbound", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-qos2-outbound", 5, false, false, 60L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                46,
                2,
                false,
                false,
                "payload-qos2".getBytes()));
        PublishDelivery delivery = protocolEngine.handlePubRel(publisher, 46).deliveryPlan().deliveries().getFirst();

        assertEquals(
                PublishReleaseDisposition.SEND,
                protocolEngine.handlePubRec(subscriber, delivery.packetId()).disposition());
        assertEquals(1, sessionRegistry.find("subscriber-qos2-outbound").orElseThrow().inflightMessageCount());

        protocolEngine.handlePubComp(subscriber, delivery.packetId());

        assertEquals(0, sessionRegistry.find("subscriber-qos2-outbound").orElseThrow().inflightMessageCount());
    }

    // Verifies that QoS 2 messages for offline persistent subscribers are restored after reconnect.
    @Test
    void shouldResumeQueuedQos2MessageAfterReconnect() {
        ClientConnection publisher = connectClient("publisher-qos2-resume", 5, true, false, 0L);
        ClientConnection firstSubscriberConnection = connectClient("subscriber-qos2-resume", 5, false, false, 60L);
        protocolEngine.handleSubscribe(firstSubscriberConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));
        closeClientConnection(firstSubscriberConnection);

        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                47,
                2,
                false,
                false,
                "payload-qos2".getBytes()));
        protocolEngine.handlePubRel(publisher, 47);

        ClientConnection secondSubscriberConnection = connectClient("subscriber-qos2-resume", 5, false, false, 60L);
        SessionResumePlan resumeResult = protocolEngine.handleSessionResume(secondSubscriberConnection);
        List<PublishDelivery> resumedDeliveries = resumeResult.actions().stream()
                .map(ReplayPublish.class::cast)
                .map(ReplayPublish::delivery)
                .toList();

        assertEquals(1, resumedDeliveries.size());
        assertEquals(MqttQoS.EXACTLY_ONCE, resumedDeliveries.getFirst().grantedQos());
        assertTrue(resumedDeliveries.getFirst().fromOfflineQueue());
        assertEquals(1, sessionRegistry.find("subscriber-qos2-resume").orElseThrow().inflightMessageCount());
    }

    // Verifies that retained QoS 2 messages are stored and replayed through QoS 2 inflight state.
    @Test
    void shouldReplayRetainedQos2MessageAfterSubscribe() {
        ClientConnection publisher = connectClient("publisher-retained-qos2", 5, true, false, 0L);
        protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                48,
                2,
                true,
                false,
                "retained-qos2".getBytes()));
        protocolEngine.handlePubRel(publisher, 48);

        assertEquals(MqttQoS.EXACTLY_ONCE,
                retainedMessageRegistry.findExact("sensors/room-1/temperature").orElseThrow().qos());

        ClientConnection subscriber = connectClient("subscriber-retained-qos2", 5, false, false, 60L);
        SubscribeOutcome subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        assertEquals(1, subscribeResult.retainedReplayPlan().deliveries().size());
        PublishDelivery retainedDelivery = subscribeResult.retainedReplayPlan().deliveries().getFirst();
        assertEquals(MqttQoS.EXACTLY_ONCE, retainedDelivery.grantedQos());
        assertNotNull(retainedDelivery.packetId());
        assertEquals(1, sessionRegistry.find("subscriber-retained-qos2").orElseThrow().inflightMessageCount());
    }

    // Verifies that offline non-persistent sessions do not retain QoS 1 messages for later delivery.
    @Test
    void shouldNotQueueQos1MessageForOfflineCleanSession() {
        ClientConnection publisher = connectClient("publisher-qos1-clean", 4, true, false, null);
        ClientConnection subscriber = connectClient("subscriber-qos1-clean", 4, true, false, null);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));
        closeClientConnection(subscriber);

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                24,
                1,
                false,
                false,
                "payload".getBytes()));

        assertFalse(result.disconnectAction().isDisconnect());
        assertEquals(0, result.deliveryPlan().queuedMessageCount());
    }

    // Verifies that multiple matching subscriptions for one client produce one delivery with all identifiers.
    @Test
    void shouldMergeSubscriptionIdentifiersForMultipleMatchesToSameClient() {
        ClientConnection publisher = connectClient("publisher-multi-identifier", 5, true, false, 0L);
        ClientConnection subscriber = connectClient("subscriber-multi-identifier", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem(
                        "sensors/#",
                        0,
                        false,
                        false,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                        31),
                new SubscriptionItem(
                        "sensors/+/temperature",
                        1,
                        false,
                        false,
                        RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                        32))));

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                24,
                1,
                false,
                false,
                "payload".getBytes()));

        assertEquals(1, result.deliveryPlan().deliveries().size());
        PublishDelivery delivery = result.deliveryPlan().deliveries().getFirst();
        assertEquals(MqttQoS.AT_LEAST_ONCE, delivery.grantedQos());
        assertEquals(Set.of(31, 32), new HashSet<>(delivery.subscriptionIdentifiers()));
    }

    // Verifies that topic names containing subscription wildcards are rejected for publish.
    @Test
    void shouldRejectPublishWithInvalidTopicName() {
        ClientConnection publisher = connectClient("publisher-invalid-topic", 5, true, false, 0L);

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/+/temperature",
                0,
                0,
                false,
                false,
                "payload".getBytes()));

        assertTrue(result.disconnectAction().isDisconnect());
        assertTrue(result.deliveryPlan().isEmpty());
        assertEquals(MqttDisconnectReasonCode.TOPIC_NAME_INVALID, result.disconnectAction().reasonCode());
    }

    // Verifies that inbound QoS levels above the MQTT QoS 2 boundary are rejected.
    @Test
    void shouldRejectPublishWithUnsupportedQos3() {
        ClientConnection publisher = connectClient("publisher-qos3", 5, true, false, 0L);

        InboundPublishOutcome result = protocolEngine.handlePublish(publisher, new PublishRequest(
                "sensors/room-1/temperature",
                25,
                3,
                false,
                false,
                "payload".getBytes()));

        assertTrue(result.disconnectAction().isDisconnect());
        assertTrue(result.deliveryPlan().isEmpty());
        assertEquals(MqttDisconnectReasonCode.QOS_NOT_SUPPORTED, result.disconnectAction().reasonCode());
    }

    // Verifies that will publish authorization rejects CONNECT before session state is created.
    @Test
    void shouldRejectConnectWhenWillPublishIsUnauthorized() {
        protocolEngine = protocolEngineWithAuthz(context ->
                "status/client-will".equals(context.topic())
                        ? AuthzResult.deny(AuthzReason.NOT_AUTHORIZED)
                        : AuthzResult.allow());
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "client-will", "MQTT", 5, true);

        ConnectOutcome decision = protocolEngine.handleConnect(connection, mqtt5Connect(
                "client-will",
                true,
                0L,
                new WillMessage("status/client-will", "offline".getBytes(), MqttQoS.AT_MOST_ONCE, false)));

        RejectedConnectResponse response = (RejectedConnectResponse) decision.response();
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5, response.returnCode());
        assertTrue(sessionRegistry.find("client-will").isEmpty());
    }

    // Verifies that authorization receives the authenticated principal established during CONNECT.
    @Test
    void shouldExposeAuthenticatedPrincipalToAuthzContext() {
        AtomicReference<String> capturedPrincipal = new AtomicReference<>();
        AuthnProvider authnProvider = (connection, request) -> AuthnResult.allow("principal-a");
        AuthzProvider authzProvider = context -> {
            capturedPrincipal.set(context.principal());
            return AuthzResult.allow();
        };
        protocolEngine = new DefaultProtocolEngine(
                authnProvider,
                authzProvider,
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock);
        ClientConnection connection = connectClient("client-principal", 5, true, false, 0L);

        protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        assertEquals("principal-a", connection.principal());
        assertEquals("principal-a", capturedPrincipal.get());
    }

    // Verifies that an explicit disconnect changes connection state but defers session cleanup until the socket closes.
    @Test
    void shouldKeepSessionBoundUntilConnectionActuallyCloses() {
        ClientConnection connection = connectClient("disconnect-client", 5, false, false, 60L);

        protocolEngine.handleDisconnect(connection);

        assertEquals(ConnectionState.DISCONNECTING, connection.state());
        assertEquals(connection.connectionId(),
                sessionRegistry.find("disconnect-client").orElseThrow().connectionId());
    }

    // Verifies that an explicit DISCONNECT suppresses any stored will message when the socket later closes.
    @Test
    void shouldNotPublishWillAfterExplicitDisconnect() {
        ClientConnection publisher = connectClient(
                "publisher-explicit-disconnect",
                5,
                false,
                false,
                60L,
                new WillMessage("status/publisher-explicit-disconnect", "offline".getBytes(), MqttQoS.AT_MOST_ONCE, true));
        ClientConnection subscriber = connectClient("subscriber-explicit-disconnect", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("status/+", 0))));

        protocolEngine.handleDisconnect(publisher);
        closeClientConnection(publisher);

        assertTrue(retainedMessageRegistry.findExact("status/publisher-explicit-disconnect").isEmpty());
    }

    // Verifies that an abnormal close publishes the stored will through the normal publish path.
    @Test
    void shouldPublishWillAfterAbnormalClose() {
        ClientConnection publisher = connectClient(
                "publisher-will-abnormal",
                5,
                false,
                false,
                60L,
                new WillMessage("status/publisher-will-abnormal", "offline".getBytes(), MqttQoS.AT_MOST_ONCE, true));

        protocolEngine.handleConnectionClosed(publisher);
        connectionRegistry.close(publisher.connectionId());

        assertTrue(retainedMessageRegistry.findExact("status/publisher-will-abnormal").isPresent());
    }

    // Verifies that will user properties are published through the normal publish delivery path.
    @Test
    void shouldPublishWillWithUserPropertiesAfterAbnormalClose() {
        ClientConnection subscriber = connectClient("subscriber-will-user-properties", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("status/+", 0))));
        ClientConnection publisher = connectClient(
                "publisher-will-user-properties",
                5,
                false,
                false,
                60L,
                new WillMessage(
                        "status/publisher-will-user-properties",
                        "offline".getBytes(),
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        userProperties(new MqttUserProperty("trace", "will"))));

        List<PublishDelivery> deliveries = protocolEngine.handleConnectionClosed(publisher);

        assertEquals(
                List.of(new MqttUserProperty("trace", "will")),
                deliveryFor(deliveries, "subscriber-will-user-properties").properties().userProperties().values());
    }

    // Verifies that will publishes preserve MQTT 5 request-response properties.
    @Test
    void shouldPublishWillRequestResponsePropertiesAfterAbnormalClose() {
        ClientConnection subscriber = connectClient("subscriber-will-request-response", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("status/+", 0))));
        ClientConnection publisher = connectClient(
                "publisher-will-request-response",
                5,
                false,
                false,
                60L,
                new WillMessage(
                        "status/publisher-will-request-response",
                        "offline".getBytes(),
                        MqttQoS.AT_MOST_ONCE,
                        false,
                        requestResponseProperties("responses/client-a", new byte[]{1, 2, 3})));

        List<PublishDelivery> deliveries = protocolEngine.handleConnectionClosed(publisher);

        PublishProperties properties = deliveryFor(deliveries, "subscriber-will-request-response").properties();
        assertEquals("responses/client-a", properties.responseTopic());
        assertArrayEquals(new byte[]{1, 2, 3}, properties.correlationData());
    }

    // Verifies that a server-originated QoS 2 will is routed immediately instead of entering inbound QoS 2 handshaking.
    @Test
    void shouldPublishQos2WillAfterAbnormalClose() {
        ClientConnection subscriber = connectClient("subscriber-qos2-will", 5, true, false, 0L);
        protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("status/+", 2))));
        ClientConnection publisher = connectClient(
                "publisher-qos2-will",
                5,
                false,
                false,
                60L,
                new WillMessage(
                        "status/publisher-qos2-will",
                        "offline".getBytes(),
                        MqttQoS.EXACTLY_ONCE,
                        false));

        List<PublishDelivery> deliveries = protocolEngine.handleConnectionClosed(publisher);

        PublishDelivery delivery = deliveryFor(deliveries, "subscriber-qos2-will");
        assertEquals(MqttQoS.EXACTLY_ONCE, delivery.grantedQos());
        assertNotNull(delivery.packetId());
        assertTrue(retainedMessageRegistry.findExact("status/publisher-qos2-will").isEmpty());
    }

    // Verifies that retained will user properties are saved and replayed with the retained message.
    @Test
    void shouldReplayRetainedWillWithUserProperties() {
        ClientConnection publisher = connectClient(
                "publisher-retained-will-user-properties",
                5,
                false,
                false,
                60L,
                new WillMessage(
                        "status/publisher-retained-will-user-properties",
                        "offline".getBytes(),
                        MqttQoS.AT_MOST_ONCE,
                        true,
                        userProperties(new MqttUserProperty("trace", "retained-will"))));
        protocolEngine.handleConnectionClosed(publisher);
        ClientConnection subscriber = connectClient("subscriber-retained-will-user-properties", 5, true, false, 0L);

        SubscribeOutcome outcome = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("status/+", 0))));

        assertEquals(
                List.of(new MqttUserProperty("trace", "retained-will")),
                deliveryFor(
                                outcome.retainedReplayPlan().deliveries(),
                                "subscriber-retained-will-user-properties")
                        .properties()
                        .userProperties()
                        .values());
    }

    // Verifies that a retained QoS 2 will is saved and replayed using QoS 2 outbound state.
    @Test
    void shouldReplayRetainedQos2Will() {
        ClientConnection publisher = connectClient(
                "publisher-retained-qos2-will",
                5,
                false,
                false,
                60L,
                new WillMessage(
                        "status/publisher-retained-qos2-will",
                        "offline".getBytes(),
                        MqttQoS.EXACTLY_ONCE,
                        true));
        protocolEngine.handleConnectionClosed(publisher);

        assertEquals(MqttQoS.EXACTLY_ONCE,
                retainedMessageRegistry.findExact("status/publisher-retained-qos2-will").orElseThrow().qos());

        ClientConnection subscriber = connectClient("subscriber-retained-qos2-will", 5, true, false, 0L);
        SubscribeOutcome outcome = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("status/+", 2))));

        PublishDelivery retainedDelivery = deliveryFor(
                outcome.retainedReplayPlan().deliveries(),
                "subscriber-retained-qos2-will");
        assertEquals(MqttQoS.EXACTLY_ONCE, retainedDelivery.grantedQos());
        assertNotNull(retainedDelivery.packetId());
    }

    // Verifies that closing a superseded connection does not accidentally unbind the newer takeover session.
    @Test
    void shouldKeepNewSessionBindingWhenSupersededConnectionCloses() {
        ClientConnection firstConnection = connectClient("takeover-client", 5, false, false, 60L);
        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "takeover-client", "MQTT", 5, false);

        ConnectOutcome secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "takeover-client",
                false,
                60L));
        assertTrue(secondDecision.takeoverPlan().requiresTakeover());

        closeClientConnection(firstConnection);

        assertEquals(ConnectionState.CLOSED, firstConnection.state());
        assertEquals(secondConnection.connectionId(),
                sessionRegistry.find("takeover-client").orElseThrow().connectionId());
    }

    private ClientConnection connectClient(
            String clientId,
            int protocolVersion,
            boolean cleanSession,
            boolean cleanStart,
            Long sessionExpiryIntervalSeconds) {
        return connectClient(clientId, protocolVersion, cleanSession, cleanStart, sessionExpiryIntervalSeconds, null);
    }

    private ClientConnection connectClient(
            String clientId,
            int protocolVersion,
            boolean cleanSession,
            boolean cleanStart,
            Long sessionExpiryIntervalSeconds,
            WillMessage willMessage) {
        return connectClient(clientId, protocolVersion, cleanSession, cleanStart, sessionExpiryIntervalSeconds, willMessage, 65_535);
    }

    private ClientConnection connectClient(
            String clientId,
            int protocolVersion,
            boolean cleanSession,
            boolean cleanStart,
            Long sessionExpiryIntervalSeconds,
            WillMessage willMessage,
            int receiveMaximum) {
        return connectClient(
                clientId,
                protocolVersion,
                cleanSession,
                cleanStart,
                sessionExpiryIntervalSeconds,
                willMessage,
                receiveMaximum,
                268_435_455);
    }

    private ClientConnection connectClient(
            String clientId,
            int protocolVersion,
            boolean cleanSession,
            boolean cleanStart,
            Long sessionExpiryIntervalSeconds,
            WillMessage willMessage,
            int receiveMaximum,
            int maximumPacketSize) {
        ClientConnection connection = connectionRegistry.open(
                "127.0.0.1",
                clientId,
                "MQTT",
                protocolVersion,
                protocolVersion == 4 ? cleanSession : cleanStart);
        ConnectOutcome decision = protocolEngine.handleConnect(connection, protocolVersion == 4
                ? mqtt311Connect(clientId, cleanSession, willMessage)
                : mqtt5Connect(
                        clientId,
                        cleanStart,
                        sessionExpiryIntervalSeconds,
                        willMessage,
                        receiveMaximum,
                        maximumPacketSize));
        assertTrue(decision.response() instanceof AcceptedConnectResponse);
        return connection;
    }

    private ConnectRequest mqtt311Connect(String clientId, boolean cleanSession) {
        return mqtt311Connect(clientId, cleanSession, null);
    }

    private ConnectRequest mqtt311Connect(String clientId, boolean cleanSession, WillMessage willMessage) {
        return new Mqtt311ConnectRequest(clientId, "MQTT", cleanSession, null, false, willMessage);
    }

    private ConnectRequest mqtt5Connect(String clientId, boolean cleanStart, long sessionExpiryIntervalSeconds) {
        return mqtt5Connect(clientId, cleanStart, sessionExpiryIntervalSeconds, null);
    }

    private ConnectRequest mqtt5Connect(
            String clientId,
            boolean cleanStart,
            long sessionExpiryIntervalSeconds,
            WillMessage willMessage) {
        return mqtt5Connect(clientId, cleanStart, sessionExpiryIntervalSeconds, willMessage, 65_535);
    }

    private ConnectRequest mqtt5Connect(
            String clientId,
            boolean cleanStart,
            long sessionExpiryIntervalSeconds,
            WillMessage willMessage,
            int receiveMaximum) {
        return mqtt5Connect(clientId, cleanStart, sessionExpiryIntervalSeconds, willMessage, receiveMaximum, 268_435_455);
    }

    private ConnectRequest mqtt5Connect(
            String clientId,
            boolean cleanStart,
            long sessionExpiryIntervalSeconds,
            WillMessage willMessage,
            int receiveMaximum,
            int maximumPacketSize) {
        return new Mqtt5ConnectRequest(
                clientId,
                "MQTT",
                cleanStart,
                sessionExpiryIntervalSeconds,
                null,
                false,
                willMessage,
                new ConnectProperties(MqttUserProperties.empty(), receiveMaximum, maximumPacketSize));
    }

    private PublishProperties userProperties(MqttUserProperty... userProperties) {
        return new PublishProperties(new MqttUserProperties(List.of(userProperties)));
    }

    private PublishProperties messageExpiryAt(Instant expiresAt) {
        return new PublishProperties(MqttUserProperties.empty(), new MessageExpiry(expiresAt));
    }

    private PublishProperties requestResponseProperties(String responseTopic, byte[] correlationData) {
        return new PublishProperties(
                MqttUserProperties.empty(),
                MessageExpiry.none(),
                responseTopic,
                correlationData);
    }

    private DefaultProtocolEngine protocolEngineWith(SubscriptionRegistry subscriptionRegistry) {
        return new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock);
    }

    private DefaultProtocolEngine protocolEngineWithAuthz(AuthzAuthorizer authorizer) {
        return new DefaultProtocolEngine(
                new PermitAllAuthnProvider(),
                new ConfiguredAuthzProvider(new AuthzChain(
                        List.of(new AuthzDefinition("test", true, authorizer)),
                        AuthzNoMatchPolicy.DENY)),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry,
                clock);
    }

    private PublishDelivery deliveryFor(List<PublishDelivery> deliveries, String clientId) {
        return deliveries.stream()
                .filter(delivery -> clientId.equals(delivery.clientId()))
                .findFirst()
                .orElseThrow();
    }

    private void closeClientConnection(ClientConnection connection) {
        protocolEngine.handleConnectionClosed(connection);
        connectionRegistry.close(connection.connectionId());
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

    private static final class FailingSubscriptionRegistry implements SubscriptionRegistry {

        private final SubscriptionRegistry delegate;
        private final boolean failAdd;
        private final boolean failRemove;

        private FailingSubscriptionRegistry(SubscriptionRegistry delegate, boolean failAdd, boolean failRemove) {
            this.delegate = delegate;
            this.failAdd = failAdd;
            this.failRemove = failRemove;
        }

        @Override
        public void addSubscription(SubscriptionBinding subscriptionBinding) {
            if (failAdd) {
                throw new IllegalStateException("simulated routing add failure");
            }
            delegate.addSubscription(subscriptionBinding);
        }

        @Override
        public boolean removeSubscription(String clientId, String topicFilter) {
            if (failRemove) {
                throw new IllegalStateException("simulated routing remove failure");
            }
            return delegate.removeSubscription(clientId, topicFilter);
        }

        @Override
        public Collection<SubscriptionBinding> match(String topicName) {
            return delegate.match(topicName);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
