package io.github.vxmqmqtt.vxmq.transport.vertx;

import io.github.vxmqmqtt.vxmq.auth.AuthProvider;
import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.DefaultProtocolEngine;
import io.github.vxmqmqtt.vxmq.protocol.ProtocolEngine;
import io.github.vxmqmqtt.vxmq.protocol.model.*;
import io.github.vxmqmqtt.vxmq.retained.InMemoryRetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.routing.DefaultMqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import io.github.vxmqmqtt.vxmq.session.InMemorySessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttWill;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mutiny.mqtt.MqttEndpoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for transport helper behavior that does not need a live socket.
 */
class VertxMqttBrokerTransportTest {

    // Verifies that MQTT 3.1.1 takeover handling falls back to closing the socket directly.
    @Test
    void shouldCloseMqtt311EndpointDirectly() {
        EndpointProbe probe = new EndpointProbe(4, true);

        VertxMqttBrokerTransport.closeEndpointWithMqtt5Reason(
                probe.endpoint(),
                MqttDisconnectReasonCode.SESSION_TAKEN_OVER);

        assertTrue(probe.closeCalled);
        assertFalse(probe.disconnectCalled);
    }

    // Verifies that MQTT 5 takeover handling sends a DISCONNECT with the supplied reason code.
    @Test
    void shouldDisconnectMqtt5EndpointWithReasonCode() {
        EndpointProbe probe = new EndpointProbe(5, true);

        VertxMqttBrokerTransport.closeEndpointWithMqtt5Reason(
                probe.endpoint(),
                MqttDisconnectReasonCode.SESSION_TAKEN_OVER);

        assertFalse(probe.closeCalled);
        assertTrue(probe.disconnectCalled);
        assertEquals(MqttDisconnectReasonCode.SESSION_TAKEN_OVER, probe.disconnectReasonCode);
        assertEquals(MqttProperties.NO_PROPERTIES, probe.disconnectProperties);
    }

    // Verifies that rejected publishes only disconnect when the outcome explicitly requests it.
    @Test
    void shouldDisconnectOnlyWhenPublishResultRequestsConnectionClosure() throws Exception {
        InboundPublishOutcome rejectingWithoutDisconnect = InboundPublishOutcome.rejected();
        ProtocolEngine protocolEngine = protocolEngineReturning(rejectingWithoutDisconnect);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-no-disconnect");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokePublishHandler(new PublishMessageProbe("sensors/room-1/temperature", 7, 0, false, false, "payload")
                .message());

        assertFalse(probe.closeCalled);
        assertFalse(probe.disconnectCalled);
        assertFalse(probe.publishAcknowledgeCalled);
    }

    // Verifies that the publish handler still disconnects when the outcome explicitly requests connection closure.
    @Test
    void shouldDisconnectWhenPublishResultRequestsConnectionClosure() throws Exception {
        InboundPublishOutcome rejectingWithDisconnect =
                InboundPublishOutcome.rejectedWithDisconnect(MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
        ProtocolEngine protocolEngine = protocolEngineReturning(rejectingWithDisconnect);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-with-disconnect");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokePublishHandler(new PublishMessageProbe("sensors/+/temperature", 9, 0, false, false, "payload")
                .message());

        assertFalse(probe.closeCalled);
        assertTrue(probe.disconnectCalled);
        assertEquals(MqttDisconnectReasonCode.TOPIC_NAME_INVALID, probe.disconnectReasonCode);
    }

    // Verifies that accepted inbound QoS 2 publishes are acknowledged with PUBREC.
    @Test
    void shouldSendPubRecForInboundQos2Publish() throws Exception {
        ProtocolEngine protocolEngine = protocolEngineReturning(InboundPublishOutcome.deferred(
                PublishAcknowledgement.pubRec(MqttPubRecReasonCode.SUCCESS)));
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-qos2-publish");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokePublishHandler(new PublishMessageProbe("sensors/room-1/temperature", 11, 2, false, false, "payload")
                .message());

        assertTrue(probe.publishReceivedCalled);
        assertEquals(11, probe.publishReceivedPacketId);
    }

    // Verifies that inbound PUBREL is completed with PUBCOMP.
    @Test
    void shouldSendPubCompForInboundPubRel() throws Exception {
        ProtocolEngine protocolEngine = protocolEngineReturning(InboundPublishOutcome.rejected());
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-qos2-pubrel");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokePublishReleaseHandler(12);

        assertTrue(probe.publishCompleteCalled);
        assertEquals(12, probe.publishCompletePacketId);
    }

    // Verifies that subscriber PUBREC advances outbound QoS 2 by sending PUBREL.
    @Test
    void shouldSendPubRelForOutboundPubRec() throws Exception {
        ProtocolEngine protocolEngine = protocolEngineReturning(InboundPublishOutcome.rejected());
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-qos2-pubrec");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokePublishReceivedHandler(13);

        assertTrue(probe.publishReleaseCalled);
        assertEquals(13, probe.publishReleasePacketId);
    }

    // Verifies that MQTT 5 SUBSCRIBE options and Subscription Identifier are passed to the protocol engine.
    @Test
    void shouldMapMqtt5SubscribeOptionsAndIdentifier() throws Exception {
        AtomicReference<SubscriptionRequest> capturedRequest = new AtomicReference<>();
        ProtocolEngine protocolEngine = protocolEngineCapturingSubscribe(capturedRequest);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-subscribe-options");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokeSubscribeHandler(new SubscribeMessageProbe(
                "sensors/+/temperature",
                MqttQoS.AT_LEAST_ONCE,
                true,
                true,
                RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS,
                42).message());

        assertNotNull(capturedRequest.get());
        assertEquals(1, capturedRequest.get().items().size());
        assertEquals("sensors/+/temperature", capturedRequest.get().items().getFirst().topicFilter());
        assertEquals(1, capturedRequest.get().items().getFirst().requestedQos());
        assertTrue(capturedRequest.get().items().getFirst().noLocal());
        assertTrue(capturedRequest.get().items().getFirst().retainAsPublished());
        assertEquals(
                RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS,
                capturedRequest.get().items().getFirst().retainHandling());
        assertEquals(42, capturedRequest.get().items().getFirst().subscriptionIdentifier());
    }

    // Verifies that MQTT 5 SUBSCRIBE User Property values are passed to the protocol engine.
    @Test
    void shouldMapMqtt5SubscribeUserProperties() throws Exception {
        AtomicReference<SubscriptionRequest> capturedRequest = new AtomicReference<>();
        ProtocolEngine protocolEngine = protocolEngineCapturingSubscribe(capturedRequest);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-subscribe-user-properties");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokeSubscribeHandler(new SubscribeMessageProbe(
                "sensors/+/temperature",
                MqttQoS.AT_LEAST_ONCE,
                true,
                true,
                RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS,
                42,
                List.of(new MqttUserProperty("trace", "subscribe"))).message());

        assertNotNull(capturedRequest.get());
        assertEquals(
                List.of(new MqttUserProperty("trace", "subscribe")),
                capturedRequest.get().properties().userProperties().values());
        assertEquals(42, capturedRequest.get().items().getFirst().subscriptionIdentifier());
    }

    // Verifies that MQTT 3.1.1 SUBSCRIBE does not expose MQTT 5 user properties.
    @Test
    void shouldUseEmptyUserPropertiesForMqtt311Subscribe() throws Exception {
        AtomicReference<SubscriptionRequest> capturedRequest = new AtomicReference<>();
        ProtocolEngine protocolEngine = protocolEngineCapturingSubscribe(capturedRequest);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-mqtt311-subscribe", 4);
        EndpointProbe probe = new EndpointProbe(4, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokeSubscribeHandler(new SubscribeMessageProbe(
                "sensors/+/temperature",
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                RetainedHandlingPolicy.SEND_AT_SUBSCRIBE,
                42,
                List.of(new MqttUserProperty("trace", "ignored"))).message());

        assertNotNull(capturedRequest.get());
        assertTrue(capturedRequest.get().properties().userProperties().isEmpty());
    }

    // Verifies that MQTT 5 UNSUBSCRIBE User Property values are passed to the protocol engine.
    @Test
    void shouldMapMqtt5UnsubscribeUserProperties() throws Exception {
        AtomicReference<UnsubscribeRequest> capturedRequest = new AtomicReference<>();
        ProtocolEngine protocolEngine = protocolEngineCapturingUnsubscribe(capturedRequest);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-unsubscribe-user-properties");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokeUnsubscribeHandler(new UnsubscribeMessageProbe(
                List.of("sensors/+/temperature"),
                List.of(new MqttUserProperty("trace", "unsubscribe"))).message());

        assertNotNull(capturedRequest.get());
        assertEquals(List.of("sensors/+/temperature"), capturedRequest.get().topicFilters());
        assertEquals(
                List.of(new MqttUserProperty("trace", "unsubscribe")),
                capturedRequest.get().properties().userProperties().values());
    }

    // Verifies that MQTT 3.1.1 UNSUBSCRIBE does not expose MQTT 5 user properties.
    @Test
    void shouldUseEmptyUserPropertiesForMqtt311Unsubscribe() throws Exception {
        AtomicReference<UnsubscribeRequest> capturedRequest = new AtomicReference<>();
        ProtocolEngine protocolEngine = protocolEngineCapturingUnsubscribe(capturedRequest);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-mqtt311-unsubscribe", 4);
        EndpointProbe probe = new EndpointProbe(4, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokeUnsubscribeHandler(new UnsubscribeMessageProbe(
                List.of("sensors/+/temperature"),
                List.of(new MqttUserProperty("trace", "ignored"))).message());

        assertNotNull(capturedRequest.get());
        assertTrue(capturedRequest.get().properties().userProperties().isEmpty());
    }

    // Verifies that outbound MQTT 5 publishes include all matching Subscription Identifier properties.
    @Test
    void shouldAddSubscriptionIdentifiersToMqtt5PublishProperties() throws Exception {
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngineReturning(InboundPublishOutcome.rejected()),
                new ClientConnectionRegistry(),
                brokerEventSink());
        EndpointProbe probe = new EndpointProbe(5, true);

        publishToSubscriber(transport, probe.endpoint(), new PublishDelivery(
                "subscriber-with-identifiers",
                "sensors/room-1/temperature",
                "payload".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                12,
                false,
                List.of(7, 9)));

        assertNotNull(probe.publishProperties);
        assertEquals(
                List.of(7, 9),
                probe.publishProperties.getProperties(
                                MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value())
                        .stream()
                        .map(MqttProperties.MqttProperty::value)
                        .toList());
    }

    // Verifies that MQTT 5 inbound PUBLISH User Property values are passed to the protocol engine.
    @Test
    void shouldMapMqtt5PublishUserProperties() throws Exception {
        AtomicReference<PublishRequest> capturedRequest = new AtomicReference<>();
        ProtocolEngine protocolEngine = protocolEngineCapturingPublish(capturedRequest);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-publish-user-properties");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokePublishHandler(new PublishMessageProbe(
                "sensors/room-1/temperature",
                7,
                0,
                false,
                false,
                "payload",
                userProperties(
                        new MqttUserProperty("trace", "a"),
                        new MqttUserProperty("trace", "b"))).message());

        assertNotNull(capturedRequest.get());
        assertEquals(
                List.of(new MqttUserProperty("trace", "a"), new MqttUserProperty("trace", "b")),
                capturedRequest.get().properties().userProperties().values());
    }

    // Verifies that MQTT 5 inbound PUBLISH Message Expiry Interval is passed to the protocol engine.
    @Test
    void shouldMapMqtt5PublishMessageExpiryInterval() throws Exception {
        AtomicReference<PublishRequest> capturedRequest = new AtomicReference<>();
        ProtocolEngine protocolEngine = protocolEngineCapturingPublish(capturedRequest);
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngine,
                new ClientConnectionRegistry(),
                brokerEventSink());
        ClientConnection connection = connectedClient("client-publish-expiry");
        EndpointProbe probe = new EndpointProbe(5, true);

        installHandlers(transport, connection, probe.endpoint());
        probe.invokePublishHandler(new PublishMessageProbe(
                "sensors/room-1/temperature",
                7,
                0,
                false,
                false,
                "payload",
                messageExpiryInterval(30L)).message());

        assertNotNull(capturedRequest.get());
        assertFalse(capturedRequest.get().properties().messageExpiry().isEmpty());
        long remaining = capturedRequest.get()
                .properties()
                .messageExpiry()
                .remainingIntervalSeconds(Instant.now())
                .orElseThrow();
        assertTrue(remaining > 0L);
        assertTrue(remaining <= 30L);
    }

    // Verifies that outbound MQTT 5 publishes include both User Property and Subscription Identifier properties.
    @Test
    void shouldAddUserPropertiesAndSubscriptionIdentifiersToMqtt5PublishProperties() throws Exception {
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngineReturning(InboundPublishOutcome.rejected()),
                new ClientConnectionRegistry(),
                brokerEventSink());
        EndpointProbe probe = new EndpointProbe(5, true);

        publishToSubscriber(transport, probe.endpoint(), new PublishDelivery(
                "subscriber-with-user-properties",
                "sensors/room-1/temperature",
                "payload".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                12,
                false,
                userProperties(
                        new MqttUserProperty("trace", "a"),
                        new MqttUserProperty("trace", "b")),
                List.of(7)));

        assertNotNull(probe.publishProperties);
        assertEquals(
                List.of("trace=a", "trace=b"),
                probe.publishProperties.getProperties(MqttProperties.MqttPropertyType.USER_PROPERTY.value())
                        .stream()
                        .map(property -> {
                            MqttProperties.StringPair pair =
                                    (MqttProperties.StringPair) property.value();
                            return pair.key + "=" + pair.value;
                        })
                        .toList());
        assertEquals(
                List.of(7),
                probe.publishProperties.getProperties(
                                MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value())
                        .stream()
                .map(MqttProperties.MqttProperty::value)
                .toList());
    }

    // Verifies that MQTT 5 outbound publishes include Message Expiry Interval with other publish properties.
    @Test
    void shouldAddMessageExpiryToMqtt5PublishProperties() throws Exception {
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngineReturning(InboundPublishOutcome.rejected()),
                new ClientConnectionRegistry(),
                brokerEventSink());
        EndpointProbe probe = new EndpointProbe(5, true);

        publishToSubscriber(transport, probe.endpoint(), new PublishDelivery(
                "subscriber-with-expiry",
                "sensors/room-1/temperature",
                "payload".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                12,
                false,
                new PublishProperties(
                        new MqttUserProperties(List.of(new MqttUserProperty("trace", "a"))),
                        MessageExpiry.fromIntervalSeconds(30L, Instant.now())),
                List.of(7)));

        assertNotNull(probe.publishProperties);
        MqttProperties.MqttProperty<?> expiryProperty = probe.publishProperties.getProperty(
                MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value());
        assertNotNull(expiryProperty);
        int remaining = (Integer) expiryProperty.value();
        assertTrue(remaining > 0);
        assertTrue(remaining <= 30);
        assertEquals(
                List.of("trace=a"),
                probe.publishProperties.getProperties(MqttProperties.MqttPropertyType.USER_PROPERTY.value())
                        .stream()
                        .map(property -> {
                            MqttProperties.StringPair pair =
                                    (MqttProperties.StringPair) property.value();
                            return pair.key + "=" + pair.value;
                        })
                        .toList());
        assertEquals(
                List.of(7),
                probe.publishProperties.getProperties(
                                MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value())
                        .stream()
                        .map(MqttProperties.MqttProperty::value)
                        .toList());
    }

    // Verifies that MQTT 3.1.1 outbound publishes do not use MQTT 5 properties.
    @Test
    void shouldNotAddPublishPropertiesForMqtt311OutboundPublish() throws Exception {
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngineReturning(InboundPublishOutcome.rejected()),
                new ClientConnectionRegistry(),
                brokerEventSink());
        EndpointProbe probe = new EndpointProbe(4, true);

        publishToSubscriber(transport, probe.endpoint(), new PublishDelivery(
                "mqtt311-subscriber",
                "sensors/room-1/temperature",
                "payload".getBytes(),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false,
                12,
                false,
                userProperties(new MqttUserProperty("trace", "a")),
                List.of(7)));

        assertNull(probe.publishProperties);
    }

    // Verifies that MQTT 5 CONNECT will properties are mapped onto the broker will model.
    @Test
    void shouldMapMqtt5WillUserProperties() throws Exception {
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngineReturning(InboundPublishOutcome.rejected()),
                new ClientConnectionRegistry(),
                brokerEventSink());
        MqttProperties willProperties = new MqttProperties();
        willProperties.add(new MqttProperties.UserProperty("trace", "will"));
        willProperties.add(new MqttProperties.UserProperty("source", "connect"));
        MqttWill will = new MqttWill(
                true,
                "status/client-will",
                Buffer.buffer("offline"),
                1,
                true,
                willProperties);

        ConnectRequest request = buildConnectRequest(transport, new ConnectEndpointProbe(5, will).endpoint());

        assertEquals(
                List.of(new MqttUserProperty("trace", "will"), new MqttUserProperty("source", "connect")),
                request.willMessage().properties().userProperties().values());
    }

    // Verifies that MQTT 5 CONNECT User Property values are exposed to downstream auth providers.
    @Test
    void shouldExposeMqtt5ConnectUserPropertiesToAuthProvider() throws Exception {
        AtomicReference<ConnectRequest> capturedRequest = new AtomicReference<>();
        AuthProvider authProvider = (connection, request) -> {
            capturedRequest.set(request);
            return request.properties().userProperties().values().contains(new MqttUserProperty("auth-hint", "allow"));
        };
        MqttTopicSupport topicSupport = new DefaultMqttTopicSupport();
        DefaultProtocolEngine protocolEngine =
                new DefaultProtocolEngine(
                        authProvider,
                        new InMemorySessionRegistry(),
                        new InMemoryRetainedMessageRegistry(topicSupport),
                        new InMemorySubscriptionRegistry(topicSupport),
                        topicSupport,
                        brokerEventSink(),
                        new ClientConnectionRegistry());
        ClientConnection connection = new ClientConnection("connection-auth", "remote", "client-auth", "MQTT", 5, true);
        MqttProperties connectProperties = new MqttProperties();
        connectProperties.add(new MqttProperties.UserProperty("auth-hint", "allow"));
        ConnectRequest request = buildConnectRequest(
                new VertxMqttBrokerTransport(null, runtimeConfig(), protocolEngine, new ClientConnectionRegistry(), brokerEventSink()),
                new ConnectEndpointProbe(5, null, connectProperties).endpoint());

        ConnectOutcome outcome = protocolEngine.handleConnect(connection, request);

        assertInstanceOf(AcceptedConnectResponse.class, outcome.response());
        assertNotNull(capturedRequest.get());
        assertEquals(List.of(new MqttUserProperty("auth-hint", "allow")),
                capturedRequest.get().properties().userProperties().values());
    }

    // Verifies that MQTT 3.1.1 will messages do not create MQTT 5 publish properties.
    @Test
    void shouldUseEmptyPropertiesForMqtt311Will() throws Exception {
        VertxMqttBrokerTransport transport = new VertxMqttBrokerTransport(
                null,
                runtimeConfig(),
                protocolEngineReturning(InboundPublishOutcome.rejected()),
                new ClientConnectionRegistry(),
                brokerEventSink());
        MqttProperties willProperties = new MqttProperties();
        willProperties.add(new MqttProperties.UserProperty("trace", "will"));
        MqttWill will = new MqttWill(
                true,
                "status/client-will",
                Buffer.buffer("offline"),
                1,
                true,
                willProperties);

        ConnectRequest request = buildConnectRequest(transport, new ConnectEndpointProbe(4, will).endpoint());

        assertTrue(request.willMessage().properties().isEmpty());
    }

    private static void installHandlers(
            VertxMqttBrokerTransport transport,
            ClientConnection connection,
            MqttEndpoint endpoint) throws Exception {
        Method method = VertxMqttBrokerTransport.class.getDeclaredMethod(
                "installHandlers",
                ClientConnection.class,
                MqttEndpoint.class);
        method.setAccessible(true);
        method.invoke(transport, connection, endpoint);
    }

    private static ConnectRequest buildConnectRequest(
            VertxMqttBrokerTransport transport,
            MqttEndpoint endpoint) throws Exception {
        Method method = VertxMqttBrokerTransport.class.getDeclaredMethod("buildConnectRequest", MqttEndpoint.class);
        method.setAccessible(true);
        return (ConnectRequest) method.invoke(transport, endpoint);
    }

    @SuppressWarnings("unchecked")
    private static void publishToSubscriber(
            VertxMqttBrokerTransport transport,
            MqttEndpoint endpoint,
            PublishDelivery delivery) throws Exception {
        Method method = VertxMqttBrokerTransport.class.getDeclaredMethod(
                "outboundPublish",
                MqttEndpoint.class,
                PublishDelivery.class);
        method.setAccessible(true);
        ((Uni<Integer>) method.invoke(transport, endpoint, delivery)).await().indefinitely();
    }

    private static PublishProperties userProperties(MqttUserProperty... userProperties) {
        return new PublishProperties(new MqttUserProperties(List.of(userProperties)));
    }

    private static PublishProperties messageExpiryInterval(long intervalSeconds) {
        return new PublishProperties(
                MqttUserProperties.empty(),
                MessageExpiry.fromIntervalSeconds(intervalSeconds, Instant.now()));
    }

    private static ClientConnection connectedClient(String clientId) {
        return connectedClient(clientId, 5);
    }

    private static ClientConnection connectedClient(String clientId, int protocolVersion) {
        ClientConnection connection =
                new ClientConnection("connection-" + clientId, "remote", clientId, "MQTT", protocolVersion, true);
        connection.assignClientId(clientId);
        return connection;
    }

    private static BrokerRuntimeConfig runtimeConfig() {
        return new BrokerRuntimeConfig() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String host() {
                return "127.0.0.1";
            }

            @Override
            public int port() {
                return 1883;
            }

            @Override
            public int maxMessageSize() {
                return 1024;
            }

            @Override
            public int timeoutOnConnectSeconds() {
                return 10;
            }

            @Override
            public int offlineQueueCapacityPerSession() {
                return 8;
            }
        };
    }

    private static BrokerEventSink brokerEventSink() {
        return new BrokerEventSink() {
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
        };
    }

    private static ProtocolEngine protocolEngineReturning(InboundPublishOutcome publishOutcome) {
        return new ProtocolEngine() {
            @Override
            public ConnectOutcome handleConnect(ClientConnection connection, ConnectRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SubscribeOutcome handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UnsubscribeAck handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InboundPublishOutcome handlePublish(ClientConnection connection, PublishRequest request) {
                return publishOutcome;
            }

            @Override
            public SessionResumePlan handleSessionResume(ClientConnection connection) {
                return SessionResumePlan.empty();
            }

            @Override
            public void handlePubAck(ClientConnection connection, int packetId) {
            }

            @Override
            public InboundPubRelOutcome handlePubRel(ClientConnection connection, int packetId) {
                return InboundPubRelOutcome.alreadyComplete();
            }

            @Override
            public OutboundPubRecOutcome handlePubRec(ClientConnection connection, int packetId) {
                return OutboundPubRecOutcome.send(io.vertx.mqtt.messages.codes.MqttPubRelReasonCode.SUCCESS);
            }

            @Override
            public void handlePubComp(ClientConnection connection, int packetId) {
            }

            @Override
            public void handleDisconnect(ClientConnection connection) {
            }

            @Override
            public List<PublishDelivery> handleConnectionClosed(ClientConnection connection) {
                return List.of();
            }
        };
    }

    private static ProtocolEngine protocolEngineCapturingSubscribe(AtomicReference<SubscriptionRequest> capturedRequest) {
        return new ProtocolEngine() {
            @Override
            public ConnectOutcome handleConnect(ClientConnection connection, ConnectRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SubscribeOutcome handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
                capturedRequest.set(request);
                return new SubscribeOutcome(
                        new io.github.vxmqmqtt.vxmq.protocol.model.SubscribeAck(List.of()),
                        io.github.vxmqmqtt.vxmq.protocol.model.RetainedReplayPlan.empty());
            }

            @Override
            public UnsubscribeAck handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InboundPublishOutcome handlePublish(ClientConnection connection, PublishRequest request) {
                return InboundPublishOutcome.rejected();
            }

            @Override
            public SessionResumePlan handleSessionResume(ClientConnection connection) {
                return SessionResumePlan.empty();
            }

            @Override
            public void handlePubAck(ClientConnection connection, int packetId) {
            }

            @Override
            public InboundPubRelOutcome handlePubRel(ClientConnection connection, int packetId) {
                return InboundPubRelOutcome.alreadyComplete();
            }

            @Override
            public OutboundPubRecOutcome handlePubRec(ClientConnection connection, int packetId) {
                return OutboundPubRecOutcome.send(io.vertx.mqtt.messages.codes.MqttPubRelReasonCode.SUCCESS);
            }

            @Override
            public void handlePubComp(ClientConnection connection, int packetId) {
            }

            @Override
            public void handleDisconnect(ClientConnection connection) {
            }

            @Override
            public List<PublishDelivery> handleConnectionClosed(ClientConnection connection) {
                return List.of();
            }
        };
    }

    private static ProtocolEngine protocolEngineCapturingUnsubscribe(
            AtomicReference<UnsubscribeRequest> capturedRequest) {
        return new ProtocolEngine() {
            @Override
            public ConnectOutcome handleConnect(ClientConnection connection, ConnectRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SubscribeOutcome handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UnsubscribeAck handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request) {
                capturedRequest.set(request);
                return new UnsubscribeAck(List.of());
            }

            @Override
            public InboundPublishOutcome handlePublish(ClientConnection connection, PublishRequest request) {
                return InboundPublishOutcome.rejected();
            }

            @Override
            public SessionResumePlan handleSessionResume(ClientConnection connection) {
                return SessionResumePlan.empty();
            }

            @Override
            public void handlePubAck(ClientConnection connection, int packetId) {
            }

            @Override
            public InboundPubRelOutcome handlePubRel(ClientConnection connection, int packetId) {
                return InboundPubRelOutcome.alreadyComplete();
            }

            @Override
            public OutboundPubRecOutcome handlePubRec(ClientConnection connection, int packetId) {
                return OutboundPubRecOutcome.send(io.vertx.mqtt.messages.codes.MqttPubRelReasonCode.SUCCESS);
            }

            @Override
            public void handlePubComp(ClientConnection connection, int packetId) {
            }

            @Override
            public void handleDisconnect(ClientConnection connection) {
            }

            @Override
            public List<PublishDelivery> handleConnectionClosed(ClientConnection connection) {
                return List.of();
            }
        };
    }

    private static ProtocolEngine protocolEngineCapturingPublish(AtomicReference<PublishRequest> capturedRequest) {
        return new ProtocolEngine() {
            @Override
            public ConnectOutcome handleConnect(ClientConnection connection, ConnectRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SubscribeOutcome handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UnsubscribeAck handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InboundPublishOutcome handlePublish(ClientConnection connection, PublishRequest request) {
                capturedRequest.set(request);
                return InboundPublishOutcome.rejected();
            }

            @Override
            public SessionResumePlan handleSessionResume(ClientConnection connection) {
                return SessionResumePlan.empty();
            }

            @Override
            public void handlePubAck(ClientConnection connection, int packetId) {
            }

            @Override
            public InboundPubRelOutcome handlePubRel(ClientConnection connection, int packetId) {
                return InboundPubRelOutcome.alreadyComplete();
            }

            @Override
            public OutboundPubRecOutcome handlePubRec(ClientConnection connection, int packetId) {
                return OutboundPubRecOutcome.send(io.vertx.mqtt.messages.codes.MqttPubRelReasonCode.SUCCESS);
            }

            @Override
            public void handlePubComp(ClientConnection connection, int packetId) {
            }

            @Override
            public void handleDisconnect(ClientConnection connection) {
            }

            @Override
            public List<PublishDelivery> handleConnectionClosed(ClientConnection connection) {
                return List.of();
            }
        };
    }

    /**
     * Lightweight endpoint double that records which termination API was used and exposes installed handlers.
     */
    private static final class EndpointProbe {

        private final MqttEndpoint endpoint;
        private final int protocolVersion;
        private final boolean connected;
        private boolean closeCalled;
        private boolean disconnectCalled;
        private boolean publishAcknowledgeCalled;
        private boolean publishReceivedCalled;
        private boolean publishReleaseCalled;
        private boolean publishCompleteCalled;
        private int publishReceivedPacketId;
        private int publishReleasePacketId;
        private int publishCompletePacketId;
        private MqttProperties publishProperties;
        private MqttDisconnectReasonCode disconnectReasonCode;
        private MqttProperties disconnectProperties;
        private Handler<MqttPublishMessage> publishHandler;
        private Handler<MqttSubscribeMessage> subscribeHandler;
        private Handler<MqttUnsubscribeMessage> unsubscribeHandler;
        private Handler<Integer> publishReleaseHandler;
        private Handler<Integer> publishReceivedHandler;

        private EndpointProbe(int protocolVersion, boolean connected) {
            this.protocolVersion = protocolVersion;
            this.connected = connected;
            io.vertx.mqtt.MqttEndpoint delegate = (io.vertx.mqtt.MqttEndpoint) Proxy.newProxyInstance(
                    io.vertx.mqtt.MqttEndpoint.class.getClassLoader(),
                    new Class<?>[]{io.vertx.mqtt.MqttEndpoint.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "protocolVersion" -> this.protocolVersion;
                        case "isConnected" -> this.connected;
                        case "publishAutoAck" -> proxy;
                        case "publishHandler" -> {
                            this.publishHandler = castHandler(args[0]);
                            yield proxy;
                        }
                        case "subscribeHandler" -> {
                            this.subscribeHandler = castSubscribeHandler(args[0]);
                            yield proxy;
                        }
                        case "unsubscribeHandler" -> {
                            this.unsubscribeHandler = castUnsubscribeHandler(args[0]);
                            yield proxy;
                        }
                        case "publishReleaseHandler" -> {
                            this.publishReleaseHandler = castIntegerHandler(args[0]);
                            yield proxy;
                        }
                        case "publishReceivedHandler" -> {
                            this.publishReceivedHandler = castIntegerHandler(args[0]);
                            yield proxy;
                        }
                        case "disconnectHandler", "publishAcknowledgeHandler",
                             "publishCompletionHandler", "closeHandler" -> proxy;
                        case "publishAcknowledge" -> {
                            this.publishAcknowledgeCalled = true;
                            yield proxy;
                        }
                        case "publishReceived" -> {
                            this.publishReceivedCalled = true;
                            this.publishReceivedPacketId = (int) args[0];
                            yield proxy;
                        }
                        case "publishRelease" -> {
                            this.publishReleaseCalled = true;
                            this.publishReleasePacketId = (int) args[0];
                            yield proxy;
                        }
                        case "publishComplete" -> {
                            this.publishCompleteCalled = true;
                            this.publishCompletePacketId = (int) args[0];
                            yield proxy;
                        }
                        case "subscribeAcknowledge", "unsubscribeAcknowledge" -> proxy;
                        case "publish" -> {
                            for (Object arg : args) {
                                if (arg instanceof MqttProperties properties) {
                                    this.publishProperties = properties;
                                }
                            }
                            if (args.length > 0 && args[args.length - 1] instanceof Handler<?> handler) {
                                @SuppressWarnings("unchecked")
                                Handler<io.vertx.core.AsyncResult<Integer>> publishHandler =
                                        (Handler<io.vertx.core.AsyncResult<Integer>>) handler;
                                publishHandler.handle(Future.succeededFuture(1));
                                yield proxy;
                            }
                            yield Future.succeededFuture(1);
                        }
                        case "close" -> {
                            this.closeCalled = true;
                            yield null;
                        }
                        case "disconnect" -> {
                            this.disconnectCalled = true;
                            this.disconnectReasonCode = (MqttDisconnectReasonCode) args[0];
                            this.disconnectProperties = (MqttProperties) args[1];
                            yield proxy;
                        }
                        case "toString" -> "EndpointProbe";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("Unsupported method: " + method.getName());
                    });
            this.endpoint = new MqttEndpoint(delegate);
        }

        private MqttEndpoint endpoint() {
            return endpoint;
        }

        private void invokePublishHandler(MqttPublishMessage message) {
            assertNotNull(publishHandler);
            publishHandler.handle(message);
        }

        private void invokeSubscribeHandler(MqttSubscribeMessage message) {
            assertNotNull(subscribeHandler);
            subscribeHandler.handle(message);
        }

        private void invokeUnsubscribeHandler(MqttUnsubscribeMessage message) {
            assertNotNull(unsubscribeHandler);
            unsubscribeHandler.handle(message);
        }

        private void invokePublishReleaseHandler(int packetId) {
            assertNotNull(publishReleaseHandler);
            publishReleaseHandler.handle(packetId);
        }

        private void invokePublishReceivedHandler(int packetId) {
            assertNotNull(publishReceivedHandler);
            publishReceivedHandler.handle(packetId);
        }

        @SuppressWarnings("unchecked")
        private static Handler<MqttPublishMessage> castHandler(Object value) {
            return (Handler<MqttPublishMessage>) value;
        }

        @SuppressWarnings("unchecked")
        private static Handler<MqttSubscribeMessage> castSubscribeHandler(Object value) {
            return (Handler<MqttSubscribeMessage>) value;
        }

        @SuppressWarnings("unchecked")
        private static Handler<MqttUnsubscribeMessage> castUnsubscribeHandler(Object value) {
            return (Handler<MqttUnsubscribeMessage>) value;
        }

        @SuppressWarnings("unchecked")
        private static Handler<Integer> castIntegerHandler(Object value) {
            return (Handler<Integer>) value;
        }
    }

    /**
     * Lightweight endpoint double for invoking CONNECT request mapping.
     */
    private static final class ConnectEndpointProbe {

        private final MqttEndpoint endpoint;
        private final int protocolVersion;
        private final MqttWill will;
        private final MqttProperties connectProperties;

        private ConnectEndpointProbe(int protocolVersion, MqttWill will) {
            this(protocolVersion, will, MqttProperties.NO_PROPERTIES);
        }

        private ConnectEndpointProbe(int protocolVersion, MqttWill will, MqttProperties connectProperties) {
            this.protocolVersion = protocolVersion;
            this.will = will;
            this.connectProperties = connectProperties;
            io.vertx.mqtt.MqttEndpoint delegate = (io.vertx.mqtt.MqttEndpoint) Proxy.newProxyInstance(
                    io.vertx.mqtt.MqttEndpoint.class.getClassLoader(),
                    new Class<?>[]{io.vertx.mqtt.MqttEndpoint.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "clientIdentifier" -> "client-will";
                        case "protocolName" -> "MQTT";
                        case "protocolVersion" -> this.protocolVersion;
                        case "isCleanSession" -> true;
                        case "connectProperties" -> this.connectProperties;
                        case "auth" -> null;
                        case "will" -> this.will;
                        case "toString" -> "ConnectEndpointProbe";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("Unsupported method: " + method.getName());
                    });
            this.endpoint = new MqttEndpoint(delegate);
        }

        private MqttEndpoint endpoint() {
            return endpoint;
        }
    }

    /**
     * Lightweight publish message double for invoking the installed raw Vert.x publish handler.
     */
    private static final class PublishMessageProbe {

        private final MqttPublishMessage message;

        private PublishMessageProbe(
                String topicName,
                int messageId,
                int qos,
                boolean retain,
                boolean dup,
                String payload) {
            this(topicName, messageId, qos, retain, dup, payload, PublishProperties.empty());
        }

        private PublishMessageProbe(
                String topicName,
                int messageId,
                int qos,
                boolean retain,
                boolean dup,
                String payload,
                PublishProperties publishProperties) {
            MqttProperties mqttProperties = new MqttProperties();
            for (MqttUserProperty userProperty : publishProperties.userProperties().values()) {
                mqttProperties.add(new MqttProperties.UserProperty(userProperty.key(), userProperty.value()));
            }
            publishProperties.messageExpiry()
                    .remainingIntervalSeconds(Instant.now())
                    .ifPresent(remaining -> mqttProperties.add(new MqttProperties.IntegerProperty(
                            MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value(),
                            (int) remaining)));
            this.message = (MqttPublishMessage) Proxy.newProxyInstance(
                    MqttPublishMessage.class.getClassLoader(),
                    new Class<?>[]{MqttPublishMessage.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "topicName" -> topicName;
                        case "messageId" -> messageId;
                        case "isRetain" -> retain;
                        case "isDup" -> dup;
                        case "qosLevel" -> io.netty.handler.codec.mqtt.MqttQoS.valueOf(qos);
                        case "payload" -> Buffer.buffer(payload, StandardCharsets.UTF_8.name());
                        case "properties" -> mqttProperties;
                        case "toString" -> "PublishMessageProbe";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException("Unsupported method: " + method.getName());
                    });
        }

        private MqttPublishMessage message() {
            return message;
        }
    }

    /**
     * Lightweight subscribe message double for invoking the installed raw Vert.x subscribe handler.
     */
    private static final class SubscribeMessageProbe {

        private final MqttSubscribeMessage message;

        private SubscribeMessageProbe(
                String topicFilter,
                MqttQoS qos,
                boolean noLocal,
                boolean retainAsPublished,
                RetainedHandlingPolicy retainHandling,
                int subscriptionIdentifier) {
            this(topicFilter, qos, noLocal, retainAsPublished, retainHandling, subscriptionIdentifier, List.of());
        }

        private SubscribeMessageProbe(
                String topicFilter,
                MqttQoS qos,
                boolean noLocal,
                boolean retainAsPublished,
                RetainedHandlingPolicy retainHandling,
                int subscriptionIdentifier,
                List<MqttUserProperty> userProperties) {
            io.netty.handler.codec.mqtt.MqttTopicSubscription topicSubscription =
                    new io.netty.handler.codec.mqtt.MqttTopicSubscription(
                            topicFilter,
                            new MqttSubscriptionOption(
                                    qos,
                                    noLocal,
                                    retainAsPublished,
                                    retainHandling));
            MqttProperties properties = new MqttProperties();
            properties.add(new MqttProperties.IntegerProperty(
                    MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value(),
                    subscriptionIdentifier));
            for (MqttUserProperty userProperty : userProperties) {
                properties.add(new MqttProperties.UserProperty(userProperty.key(), userProperty.value()));
            }
            this.message = MqttSubscribeMessage.create(10, List.of(topicSubscription), properties);
        }

        private MqttSubscribeMessage message() {
            return message;
        }
    }

    /**
     * Lightweight unsubscribe message double for invoking the installed raw Vert.x unsubscribe handler.
     */
    private static final class UnsubscribeMessageProbe {

        private final MqttUnsubscribeMessage message;

        private UnsubscribeMessageProbe(List<String> topicFilters, List<MqttUserProperty> userProperties) {
            MqttProperties properties = new MqttProperties();
            for (MqttUserProperty userProperty : userProperties) {
                properties.add(new MqttProperties.UserProperty(userProperty.key(), userProperty.value()));
            }
            this.message = MqttUnsubscribeMessage.create(11, topicFilters, properties);
        }

        private MqttUnsubscribeMessage message() {
            return message;
        }
    }
}
