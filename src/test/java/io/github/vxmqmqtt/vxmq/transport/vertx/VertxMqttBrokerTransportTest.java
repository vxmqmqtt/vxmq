package io.github.vxmqmqtt.vxmq.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.ProtocolEngine;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.DeliveryPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPublishOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPubRelOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.OutboundPubRecOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishAcknowledgement;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishReleaseDisposition;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumePlan;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeAck;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mutiny.mqtt.MqttEndpoint;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

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

    private static ClientConnection connectedClient(String clientId) {
        ClientConnection connection = new ClientConnection("connection-" + clientId, "remote", clientId, "MQTT", 5, true);
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
                        case "publishReleaseHandler" -> {
                            this.publishReleaseHandler = castIntegerHandler(args[0]);
                            yield proxy;
                        }
                        case "publishReceivedHandler" -> {
                            this.publishReceivedHandler = castIntegerHandler(args[0]);
                            yield proxy;
                        }
                        case "unsubscribeHandler", "disconnectHandler",
                                "publishAcknowledgeHandler", "publishCompletionHandler", "closeHandler" -> proxy;
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
                        case "subscribeAcknowledge" -> proxy;
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
        private static Handler<Integer> castIntegerHandler(Object value) {
            return (Handler<Integer>) value;
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
            this.message = MqttSubscribeMessage.create(10, List.of(topicSubscription), properties);
        }

        private MqttSubscribeMessage message() {
            return message;
        }
    }
}
