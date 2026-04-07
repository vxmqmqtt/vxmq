package io.github.vxmqmqtt.vxmq.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.ProtocolEngine;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectDecision;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mutiny.mqtt.MqttEndpoint;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    // Verifies that rejected publishes only disconnect when PublishResult explicitly requires it.
    @Test
    void shouldDisconnectOnlyWhenPublishResultRequestsConnectionClosure() throws Exception {
        PublishResult rejectingWithoutDisconnect = PublishResult.rejectedWithoutDisconnect();
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

    // Verifies that the publish handler still disconnects when the result explicitly requests connection closure.
    @Test
    void shouldDisconnectWhenPublishResultRequestsConnectionClosure() throws Exception {
        PublishResult rejectingWithDisconnect =
                PublishResult.rejectedWithDisconnect(MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
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

    private static ProtocolEngine protocolEngineReturning(PublishResult publishResult) {
        return new ProtocolEngine() {
            @Override
            public ConnectDecision handleConnect(ClientConnection connection, ConnectRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SubscribeResult handleSubscribe(ClientConnection connection, SubscriptionRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public UnsubscribeResult handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public PublishResult handlePublish(ClientConnection connection, PublishRequest request) {
                return publishResult;
            }

            @Override
            public List<PublishDelivery> handleSessionResume(ClientConnection connection) {
                return List.of();
            }

            @Override
            public void handlePubAck(ClientConnection connection, int packetId) {
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
        private MqttDisconnectReasonCode disconnectReasonCode;
        private MqttProperties disconnectProperties;
        private Handler<MqttPublishMessage> publishHandler;

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
                        case "subscribeHandler", "unsubscribeHandler", "disconnectHandler",
                                "publishAcknowledgeHandler", "closeHandler" -> proxy;
                        case "publishAcknowledge" -> {
                            this.publishAcknowledgeCalled = true;
                            yield proxy;
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

        @SuppressWarnings("unchecked")
        private static Handler<MqttPublishMessage> castHandler(Object value) {
            return (Handler<MqttPublishMessage>) value;
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
}
