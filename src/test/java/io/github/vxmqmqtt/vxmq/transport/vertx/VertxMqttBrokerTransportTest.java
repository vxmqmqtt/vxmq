package io.github.vxmqmqtt.vxmq.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mutiny.mqtt.MqttEndpoint;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for transport helper behavior that does not need a live socket.
 */
class VertxMqttBrokerTransportTest {

    @Test
    void shouldCloseMqtt311EndpointDirectly() {
        EndpointProbe probe = new EndpointProbe(4, true);

        VertxMqttBrokerTransport.closeEndpointWithMqtt5Reason(
                probe.endpoint(),
                MqttDisconnectReasonCode.SESSION_TAKEN_OVER);

        assertTrue(probe.closeCalled);
        assertFalse(probe.disconnectCalled);
    }

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

    /**
     * Lightweight endpoint double that records which termination API was used.
     */
    private static final class EndpointProbe {

        private final MqttEndpoint endpoint;
        private final int protocolVersion;
        private final boolean connected;
        private boolean closeCalled;
        private boolean disconnectCalled;
        private MqttDisconnectReasonCode disconnectReasonCode;
        private MqttProperties disconnectProperties;

        private EndpointProbe(int protocolVersion, boolean connected) {
            this.protocolVersion = protocolVersion;
            this.connected = connected;
            io.vertx.mqtt.MqttEndpoint delegate = (io.vertx.mqtt.MqttEndpoint) Proxy.newProxyInstance(
                    io.vertx.mqtt.MqttEndpoint.class.getClassLoader(),
                    new Class<?>[]{io.vertx.mqtt.MqttEndpoint.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "protocolVersion" -> this.protocolVersion;
                        case "isConnected" -> this.connected;
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
    }
}
