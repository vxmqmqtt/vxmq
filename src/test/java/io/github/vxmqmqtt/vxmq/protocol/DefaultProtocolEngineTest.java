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
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPublishOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgementType;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionItem;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.retained.InMemoryRetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
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
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
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
    private RetainedMessageRegistry retainedMessageRegistry;
    private SubscriptionRegistry subscriptionRegistry;
    private DefaultProtocolEngine protocolEngine;

    @BeforeEach
    void setUp() {
        DefaultMqttTopicSupport mqttTopicSupport = new DefaultMqttTopicSupport();
        connectionRegistry = new ClientConnectionRegistry();
        sessionRegistry = new InMemorySessionRegistry();
        retainedMessageRegistry = new InMemoryRetainedMessageRegistry(mqttTopicSupport);
        subscriptionRegistry = new InMemorySubscriptionRegistry(mqttTopicSupport);
        protocolEngine = new DefaultProtocolEngine(
                new PermitAllAuthProvider(),
                sessionRegistry,
                retainedMessageRegistry,
                subscriptionRegistry,
                mqttTopicSupport,
                new NoOpBrokerEventSink(),
                connectionRegistry);
    }

    // Verifies that MQTT 3.1.1 rejects empty client ids when the session is not clean.
    @Test
    void shouldRejectEmptyClientIdForPersistentMqtt311Session() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 4, false);

        ConnectDecision decision = protocolEngine.handleConnect(connection, mqtt311Connect(
                "",
                false));

        assertFalse(decision.accepted());
        assertEquals(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, decision.returnCode());
    }

    // Verifies that MQTT 3.1.1 clean sessions can receive an auto-generated client id.
    @Test
    void shouldAssignClientIdForMqtt311CleanSession() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 4, true);

        ConnectDecision decision = protocolEngine.handleConnect(connection, mqtt311Connect(
                "",
                true));

        assertTrue(decision.accepted());
        assertNotNull(decision.effectiveClientId());
        assertTrue(decision.effectiveClientId().startsWith("vxmq-"));
        assertTrue(decision.responseProperties().isEmpty());
        assertFalse(decision.sessionPresent());
    }

    // Verifies that MQTT 5 returns Assigned Client Identifier when the broker generates the id.
    @Test
    void shouldAssignClientIdAndConnAckPropertyForMqtt5() {
        ClientConnection connection = connectionRegistry.open("127.0.0.1", "", "MQTT", 5, true);

        ConnectDecision decision = protocolEngine.handleConnect(connection, mqtt5Connect(
                "",
                true,
                0L));

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

        ConnectDecision firstDecision = protocolEngine.handleConnect(firstConnection, mqtt5Connect(
                "client-a",
                true,
                0L));
        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "client-a",
                true,
                0L));

        assertTrue(firstDecision.accepted());
        assertTrue(secondDecision.accepted());
        assertNull(firstDecision.supersededConnectionId());
        assertEquals(firstConnection.connectionId(), secondDecision.supersededConnectionId());
    }

    // Verifies that MQTT 3.1.1 cleanSession=false restores an existing persistent session.
    @Test
    void shouldRestorePersistentMqtt311SessionOnReconnect() {
        ClientConnection firstConnection = connectionRegistry.open("127.0.0.1", "mqtt311-persistent", "MQTT", 4, false);
        ConnectDecision firstDecision = protocolEngine.handleConnect(firstConnection, mqtt311Connect(
                "mqtt311-persistent",
                false));
        assertTrue(firstDecision.accepted());
        assertFalse(firstDecision.sessionPresent());

        protocolEngine.handleSubscribe(firstConnection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));
        closeClientConnection(firstConnection);

        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "mqtt311-persistent", "MQTT", 4, false);
        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, mqtt311Connect(
                "mqtt311-persistent",
                false));

        assertTrue(secondDecision.accepted());
        assertTrue(secondDecision.sessionPresent());
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
        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, mqtt311Connect(
                "mqtt311-clean",
                true));

        assertTrue(secondDecision.accepted());
        assertFalse(secondDecision.sessionPresent());
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
        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "mqtt5-restored",
                false,
                60L));

        assertTrue(secondDecision.accepted());
        assertTrue(secondDecision.sessionPresent());
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
        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "mqtt5-fresh",
                true,
                60L));

        assertTrue(secondDecision.accepted());
        assertFalse(secondDecision.sessionPresent());
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

        SubscribeResult result = protocolEngine.handleSubscribe(connection, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        assertEquals(1, result.itemResults().size());
        assertEquals(MqttQoS.EXACTLY_ONCE, result.itemResults().getFirst().grantedQos());
        assertEquals(MqttSubAckReasonCode.GRANTED_QOS2, result.itemResults().getFirst().reasonCode());
        assertTrue(sessionRegistry.find("client-sub").orElseThrow().subscriptions().contains("sensors/+/temperature"));
        assertEquals(1, subscriptionRegistry.match("sensors/room-1/temperature").size());
    }

    // Verifies that invalid topic filters are rejected without mutating session state.
    @Test
    void shouldRejectInvalidSubscriptionFilter() {
        ClientConnection connection = connectClient("client-invalid-sub", 5, true, false, 0L);

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
        ClientConnection connection = connectClient("client-unsub", 5, true, false, 0L);
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
        ClientConnection connection = connectClient("client-unsub-missing", 5, true, false, 0L);

        UnsubscribeResult result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/+/temperature")));

        assertEquals(1, result.itemResults().size());
        assertTrue(result.itemResults().getFirst().accepted());
        assertEquals(MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED, result.itemResults().getFirst().reasonCode());
    }

    // Verifies that invalid filters are rejected during unsubscribe as well.
    @Test
    void shouldRejectInvalidUnsubscribeFilter() {
        ClientConnection connection = connectClient("client-unsub-invalid", 5, true, false, 0L);

        UnsubscribeResult result = protocolEngine.handleUnsubscribe(connection, new UnsubscribeRequest(List.of(
                "sensors/#/temperature")));

        assertEquals(1, result.itemResults().size());
        assertFalse(result.itemResults().getFirst().accepted());
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
        SubscribeResult subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 0))));

        assertEquals(1, subscribeResult.retainedDeliveries().size());
        PublishDelivery retainedDelivery = subscribeResult.retainedDeliveries().getFirst();
        assertEquals("subscriber-retained", retainedDelivery.clientId());
        assertEquals("sensors/room-1/temperature", retainedDelivery.topicName());
        assertEquals(MqttQoS.AT_MOST_ONCE, retainedDelivery.grantedQos());
        assertTrue(retainedDelivery.retain());
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
        SubscribeResult subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 1))));

        assertEquals(1, subscribeResult.retainedDeliveries().size());
        PublishDelivery retainedDelivery = subscribeResult.retainedDeliveries().getFirst();
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
        List<io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery> resumedDeliveries =
                protocolEngine.handleSessionResume(secondSubscriberConnection).deliveries();

        assertEquals(1, resumedDeliveries.size());
        assertTrue(resumedDeliveries.getFirst().fromOfflineQueue());
        assertEquals(MqttQoS.AT_LEAST_ONCE, resumedDeliveries.getFirst().grantedQos());
        assertEquals(1, sessionRegistry.find("subscriber-qos1-resume").orElseThrow().inflightMessageCount());
        assertEquals(0, sessionRegistry.find("subscriber-qos1-resume").orElseThrow().queuedMessageCount());
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

        var pubRelResult = protocolEngine.handlePubRel(publisher, 45);

        assertEquals(1, pubRelResult.deliveries().size());
        assertEquals(MqttQoS.EXACTLY_ONCE, pubRelResult.deliveries().getFirst().grantedQos());
        assertEquals(0, sessionRegistry.find("publisher-qos2-inbound").orElseThrow().inboundQos2MessageCount());
        assertEquals(1, sessionRegistry.find("subscriber-qos2-inbound").orElseThrow().inflightMessageCount());
        assertTrue(protocolEngine.handlePubRel(publisher, 45).deliveries().isEmpty());
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
        PublishDelivery delivery = protocolEngine.handlePubRel(publisher, 46).deliveries().getFirst();

        assertTrue(protocolEngine.handlePubRec(subscriber, delivery.packetId()).publishRelease());
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
        var resumeResult = protocolEngine.handleSessionResume(secondSubscriberConnection);

        assertEquals(1, resumeResult.deliveries().size());
        assertEquals(MqttQoS.EXACTLY_ONCE, resumeResult.deliveries().getFirst().grantedQos());
        assertTrue(resumeResult.deliveries().getFirst().fromOfflineQueue());
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
        SubscribeResult subscribeResult = protocolEngine.handleSubscribe(subscriber, new SubscriptionRequest(List.of(
                new SubscriptionItem("sensors/+/temperature", 2))));

        assertEquals(1, subscribeResult.retainedDeliveries().size());
        PublishDelivery retainedDelivery = subscribeResult.retainedDeliveries().getFirst();
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

    // Verifies that closing a superseded connection does not accidentally unbind the newer takeover session.
    @Test
    void shouldKeepNewSessionBindingWhenSupersededConnectionCloses() {
        ClientConnection firstConnection = connectClient("takeover-client", 5, false, false, 60L);
        ClientConnection secondConnection = connectionRegistry.open("127.0.0.1", "takeover-client", "MQTT", 5, false);

        ConnectDecision secondDecision = protocolEngine.handleConnect(secondConnection, mqtt5Connect(
                "takeover-client",
                false,
                60L));
        assertTrue(secondDecision.accepted());

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
        ClientConnection connection = connectionRegistry.open(
                "127.0.0.1",
                clientId,
                "MQTT",
                protocolVersion,
                protocolVersion == 4 ? cleanSession : cleanStart);
        ConnectDecision decision = protocolEngine.handleConnect(connection, protocolVersion == 4
                ? mqtt311Connect(clientId, cleanSession, willMessage)
                : mqtt5Connect(clientId, cleanStart, sessionExpiryIntervalSeconds, willMessage));
        assertTrue(decision.accepted());
        return connection;
    }

    private ConnectRequest mqtt311Connect(String clientId, boolean cleanSession) {
        return mqtt311Connect(clientId, cleanSession, null);
    }

    private ConnectRequest mqtt311Connect(String clientId, boolean cleanSession, WillMessage willMessage) {
        return ConnectRequest.mqtt311(
                clientId,
                "MQTT",
                cleanSession,
                null,
                false,
                willMessage);
    }

    private ConnectRequest mqtt5Connect(String clientId, boolean cleanStart, long sessionExpiryIntervalSeconds) {
        return mqtt5Connect(clientId, cleanStart, sessionExpiryIntervalSeconds, null);
    }

    private ConnectRequest mqtt5Connect(
            String clientId,
            boolean cleanStart,
            long sessionExpiryIntervalSeconds,
            WillMessage willMessage) {
        return ConnectRequest.mqtt5(
                clientId,
                "MQTT",
                cleanStart,
                sessionExpiryIntervalSeconds,
                null,
                false,
                willMessage);
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
}
