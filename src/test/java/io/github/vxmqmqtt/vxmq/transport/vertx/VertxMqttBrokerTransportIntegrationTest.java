package io.github.vxmqmqtt.vxmq.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.auth.PermitAllAuthProvider;
import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.DefaultProtocolEngine;
import io.github.vxmqmqtt.vxmq.routing.DefaultTopicMatcher;
import io.github.vxmqmqtt.vxmq.routing.InMemorySubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.session.InMemorySessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ConnectionState;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.smallrye.mutiny.Uni;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.mqtt.MqttClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests that validate broker behavior through a real MQTT transport.
 */
class VertxMqttBrokerTransportIntegrationTest {

    private Vertx vertx;
    private VertxMqttBrokerTransport transport;
    private MqttClient publisher;
    private MqttClient subscriber;
    private MqttClient duplicateClient;

    @AfterEach
    void tearDown() {
        safeDisconnect(publisher);
        safeDisconnect(subscriber);
        safeDisconnect(duplicateClient);
        if (transport != null) {
            transport.stop().await().atMost(java.time.Duration.ofSeconds(5));
        }
        if (vertx != null) {
            vertx.close().await().atMost(java.time.Duration.ofSeconds(5));
        }
    }

    // Verifies the end-to-end happy path: connect, subscribe, publish, and receive one message.
    @Test
    void shouldDeliverPublishedMessageToSubscribedClient() throws ExecutionException, InterruptedException, TimeoutException {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        subscriber = mqttClient("subscriber-1");
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());
        subscriber.subscribe("sensors/+/temperature", 0).await().indefinitely();

        publisher = mqttClient("publisher-1");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("payload"),
                MqttQoS.AT_MOST_ONCE,
                false,
                false).await().indefinitely();

        assertEquals("payload", receivedPayload.get(5, TimeUnit.SECONDS));
    }

    // Verifies that a second connection with the same client id causes the old connection to close.
    @Test
    void shouldClosePreviousConnectionWhenClientIdIsTakenOver() throws ExecutionException, InterruptedException, TimeoutException {
        int port = startBroker();
        CompletableFuture<Void> oldConnectionClosed = new CompletableFuture<>();

        subscriber = mqttClient("same-client");
        subscriber.closeHandler(() -> oldConnectionClosed.complete(null));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());

        duplicateClient = mqttClient("same-client");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, duplicateClient.connect(port, "127.0.0.1").await().indefinitely().code());

        oldConnectionClosed.get(5, TimeUnit.SECONDS);
        assertTrue(duplicateClient.isConnected());
    }

    // Verifies that unsubscribing removes the delivery path and later publishes no longer arrive.
    @Test
    void shouldStopDeliveringMessagesAfterUnsubscribe() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        subscriber = mqttClient("subscriber-unsub");
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());
        subscriber.subscribe("sensors/+/temperature", 0).await().indefinitely();
        subscriber.unsubscribe("sensors/+/temperature").await().indefinitely();

        publisher = mqttClient("publisher-after-unsub");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("payload-after-unsub"),
                MqttQoS.AT_MOST_ONCE,
                false,
                false).await().indefinitely();

        assertThrows(TimeoutException.class, () -> receivedPayload.get(1, TimeUnit.SECONDS));
    }

    // Verifies that the built-in vertx-mqtt Keep Alive handling closes idle clients.
    @Test
    void shouldCloseIdleClientWhenKeepAliveExpires() throws Exception {
        int port = startBroker();
        CompletableFuture<Void> clientClosed = new CompletableFuture<>();

        subscriber = mqttClient("idle-client", 1, false);
        subscriber.closeHandler(() -> clientClosed.complete(null));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());

        clientClosed.get(5, TimeUnit.SECONDS);
        assertFalse(subscriber.isConnected());
    }

    // Verifies that an unsupported inbound QoS triggers an abnormal disconnect.
    @Test
    void shouldCloseClientAfterUnsupportedQos1Publish() throws Exception {
        int port = startBroker();
        CompletableFuture<Void> publisherClosed = new CompletableFuture<>();

        publisher = mqttClient("publisher-qos1");
        publisher.closeHandler(() -> publisherClosed.complete(null));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());

        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("payload-qos1"),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false).await().indefinitely();

        publisherClosed.get(5, TimeUnit.SECONDS);
        assertFalse(publisher.isConnected());
    }

    private int startBroker() {
        vertx = Vertx.vertx();
        DefaultTopicMatcher topicMatcher = new DefaultTopicMatcher();
        ClientConnectionRegistry connectionRegistry = new ClientConnectionRegistry();
        transport = new VertxMqttBrokerTransport(
                vertx,
                new TestBrokerRuntimeConfig(),
                new DefaultProtocolEngine(
                        new PermitAllAuthProvider(),
                        new InMemorySessionRegistry(),
                        new InMemorySubscriptionRegistry(topicMatcher),
                        topicMatcher,
                        new NoOpBrokerEventSink(),
                        connectionRegistry),
                connectionRegistry,
                new NoOpBrokerEventSink());
        transport.start().await().indefinitely();
        return transport.actualPort();
    }

    private MqttClient mqttClient(String clientId) {
        return mqttClient(clientId, 20, true);
    }

    private MqttClient mqttClient(String clientId, int keepAliveIntervalSeconds, boolean autoKeepAlive) {
        MqttClientOptions options = new MqttClientOptions()
                .setAutoGeneratedClientId(false)
                .setClientId(clientId)
                .setCleanSession(true)
                .setKeepAliveInterval(keepAliveIntervalSeconds)
                .setAutoKeepAlive(autoKeepAlive);
        return MqttClient.create(vertx, options);
    }

    private void safeDisconnect(MqttClient client) {
        if (client == null || !client.isConnected()) {
            return;
        }
        Uni<Void> disconnect = client.disconnect();
        disconnect.await().atMost(java.time.Duration.ofSeconds(5));
    }

    /**
     * Test configuration that binds to an ephemeral local port.
     */
    private record TestBrokerRuntimeConfig() implements BrokerRuntimeConfig {

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
            return 0;
        }

        @Override
        public int maxMessageSize() {
            return 268435455;
        }

        @Override
        public int timeoutOnConnectSeconds() {
            return 10;
        }
    }

    /**
     * Test double used to silence broker event logging during integration tests.
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
