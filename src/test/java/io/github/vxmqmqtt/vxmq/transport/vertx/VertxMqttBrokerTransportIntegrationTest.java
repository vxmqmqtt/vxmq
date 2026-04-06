package io.github.vxmqmqtt.vxmq.transport.vertx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.vxmqmqtt.vxmq.auth.PermitAllAuthProvider;
import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.DefaultProtocolEngine;
import io.github.vxmqmqtt.vxmq.retained.InMemoryRetainedMessageRegistry;
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
import java.util.ArrayList;
import java.util.List;
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

    // Verifies the online QoS 1 path: publish, subscriber receives, and subscriber acknowledges.
    @Test
    void shouldDeliverAndAcknowledgeQos1MessageToOnlineSubscriber() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        subscriber = mqttClient("subscriber-qos1-online");
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());
        subscriber.subscribe("sensors/+/temperature", 1).await().indefinitely();

        publisher = mqttClient("publisher-qos1-online");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("payload-qos1"),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false).await().indefinitely();

        assertEquals("payload-qos1", receivedPayload.get(5, TimeUnit.SECONDS));
    }

    // Verifies that retained messages are sent immediately after SUBACK when a new subscriber matches them.
    @Test
    void shouldReplayRetainedMessageAfterSubscribe() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        publisher = mqttClient("publisher-retained");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("retained-payload"),
                MqttQoS.AT_MOST_ONCE,
                false,
                true).await().indefinitely();

        subscriber = mqttClient("subscriber-retained");
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());
        subscriber.subscribe("sensors/+/temperature", 0).await().indefinitely();

        assertEquals("retained-payload", receivedPayload.get(5, TimeUnit.SECONDS));
    }

    // Verifies that clearing a retained message prevents later subscribers from receiving it.
    @Test
    void shouldNotReplayRetainedMessageAfterItIsCleared() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        publisher = mqttClient("publisher-retained-clear");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("retained-payload"),
                MqttQoS.AT_MOST_ONCE,
                false,
                true).await().indefinitely();
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer(),
                MqttQoS.AT_MOST_ONCE,
                false,
                true).await().indefinitely();

        subscriber = mqttClient("subscriber-retained-clear");
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());
        subscriber.subscribe("sensors/+/temperature", 0).await().indefinitely();

        assertThrows(TimeoutException.class, () -> receivedPayload.get(1, TimeUnit.SECONDS));
    }

    // Verifies that retained QoS 1 messages are replayed through the existing QoS 1 publish path.
    @Test
    void shouldReplayRetainedQos1MessageWithAcknowledgement() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        publisher = mqttClient("publisher-retained-qos1");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("retained-qos1-payload"),
                MqttQoS.AT_LEAST_ONCE,
                false,
                true).await().indefinitely();

        subscriber = mqttClient("subscriber-retained-qos1");
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, subscriber.connect(port, "127.0.0.1").await().indefinitely().code());
        subscriber.subscribe("sensors/+/temperature", 1).await().indefinitely();

        assertEquals("retained-qos1-payload", receivedPayload.get(5, TimeUnit.SECONDS));
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

    // Verifies that a persistent MQTT 3.1.1 session restores its subscriptions after reconnect.
    @Test
    void shouldRestorePersistentSessionSubscriptionsAfterReconnect() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        subscriber = mqttClient("persistent-subscriber", false);
        assertFalse(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());
        subscriber.subscribe("sensors/+/temperature", 0).await().indefinitely();
        subscriber.disconnect().await().indefinitely();

        subscriber = mqttClient("persistent-subscriber", false);
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertTrue(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());

        publisher = mqttClient("persistent-publisher");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("persistent-payload"),
                MqttQoS.AT_MOST_ONCE,
                false,
                false).await().indefinitely();

        assertEquals("persistent-payload", receivedPayload.get(5, TimeUnit.SECONDS));
    }

    // Verifies that a persistent session receives queued QoS 1 messages after reconnecting.
    @Test
    void shouldRestoreQueuedQos1MessageAfterReconnect() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        subscriber = mqttClient("persistent-qos1-subscriber", false);
        assertFalse(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());
        subscriber.subscribe("sensors/+/temperature", 1).await().indefinitely();
        subscriber.disconnect().await().indefinitely();

        publisher = mqttClient("persistent-qos1-publisher");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("offline-qos1-payload"),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false).await().indefinitely();

        subscriber = mqttClient("persistent-qos1-subscriber", false);
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertTrue(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());

        assertEquals("offline-qos1-payload", receivedPayload.get(5, TimeUnit.SECONDS));
    }

    // Verifies that offline clean sessions do not receive queued QoS 1 messages after reconnect.
    @Test
    void shouldNotRestoreQueuedQos1MessageForCleanSession() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        subscriber = mqttClient("clean-qos1-subscriber", true);
        assertFalse(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());
        subscriber.subscribe("sensors/+/temperature", 1).await().indefinitely();
        subscriber.disconnect().await().indefinitely();

        publisher = mqttClient("clean-qos1-publisher");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("clean-qos1-payload"),
                MqttQoS.AT_LEAST_ONCE,
                false,
                false).await().indefinitely();

        subscriber = mqttClient("clean-qos1-subscriber", true);
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertFalse(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());

        assertThrows(TimeoutException.class, () -> receivedPayload.get(1, TimeUnit.SECONDS));
    }

    // Verifies that the offline queue drops the oldest QoS 1 message when the configured capacity is exceeded.
    @Test
    void shouldDropOldestQueuedQos1MessageWhenOfflineQueueIsFull() throws Exception {
        int port = startBroker(2);
        List<String> receivedPayloads = new ArrayList<>();
        CompletableFuture<Void> enoughMessages = new CompletableFuture<>();

        subscriber = mqttClient("bounded-qos1-subscriber", false);
        assertFalse(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());
        subscriber.subscribe("sensors/+/temperature", 1).await().indefinitely();
        subscriber.disconnect().await().indefinitely();

        publisher = mqttClient("bounded-qos1-publisher");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish("sensors/room-1/temperature", Buffer.buffer("first"), MqttQoS.AT_LEAST_ONCE, false, false)
                .await().indefinitely();
        publisher.publish("sensors/room-1/temperature", Buffer.buffer("second"), MqttQoS.AT_LEAST_ONCE, false, false)
                .await().indefinitely();
        publisher.publish("sensors/room-1/temperature", Buffer.buffer("third"), MqttQoS.AT_LEAST_ONCE, false, false)
                .await().indefinitely();

        subscriber = mqttClient("bounded-qos1-subscriber", false);
        subscriber.publishHandler(message -> {
            receivedPayloads.add(message.payload().toString(StandardCharsets.UTF_8));
            if (receivedPayloads.size() == 2) {
                enoughMessages.complete(null);
            }
        });
        assertTrue(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());

        enoughMessages.get(5, TimeUnit.SECONDS);
        assertEquals(List.of("second", "third"), receivedPayloads);
    }

    // Verifies that a clean MQTT 3.1.1 session discards subscriptions when the client reconnects.
    @Test
    void shouldDiscardCleanSessionSubscriptionsAfterReconnect() throws Exception {
        int port = startBroker();
        CompletableFuture<String> receivedPayload = new CompletableFuture<>();

        subscriber = mqttClient("clean-subscriber", true);
        assertFalse(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());
        subscriber.subscribe("sensors/+/temperature", 0).await().indefinitely();
        subscriber.disconnect().await().indefinitely();

        subscriber = mqttClient("clean-subscriber", true);
        subscriber.publishHandler(message ->
                receivedPayload.complete(message.payload().toString(StandardCharsets.UTF_8)));
        assertFalse(subscriber.connect(port, "127.0.0.1").await().indefinitely().isSessionPresent());

        publisher = mqttClient("clean-publisher");
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());
        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("clean-payload"),
                MqttQoS.AT_MOST_ONCE,
                false,
                false).await().indefinitely();

        assertThrows(TimeoutException.class, () -> receivedPayload.get(1, TimeUnit.SECONDS));
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
    void shouldCloseClientAfterUnsupportedQos2Publish() throws Exception {
        int port = startBroker();
        CompletableFuture<Void> publisherClosed = new CompletableFuture<>();

        publisher = mqttClient("publisher-qos2");
        publisher.closeHandler(() -> publisherClosed.complete(null));
        assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, publisher.connect(port, "127.0.0.1").await().indefinitely().code());

        publisher.publish(
                "sensors/room-1/temperature",
                Buffer.buffer("payload-qos2"),
                MqttQoS.EXACTLY_ONCE,
                false,
                false).await().indefinitely();

        publisherClosed.get(5, TimeUnit.SECONDS);
        assertFalse(publisher.isConnected());
    }

    private int startBroker() {
        return startBroker(1024);
    }

    private int startBroker(int offlineQueueCapacityPerSession) {
        vertx = Vertx.vertx();
        DefaultTopicMatcher topicMatcher = new DefaultTopicMatcher();
        ClientConnectionRegistry connectionRegistry = new ClientConnectionRegistry();
        transport = new VertxMqttBrokerTransport(
                vertx,
                new TestBrokerRuntimeConfig(offlineQueueCapacityPerSession),
                new DefaultProtocolEngine(
                        new PermitAllAuthProvider(),
                        new InMemorySessionRegistry(offlineQueueCapacityPerSession),
                        new InMemoryRetainedMessageRegistry(topicMatcher),
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

    private MqttClient mqttClient(String clientId, boolean cleanSession) {
        return mqttClient(clientId, cleanSession, 20, true);
    }

    private MqttClient mqttClient(String clientId, int keepAliveIntervalSeconds, boolean autoKeepAlive) {
        return mqttClient(clientId, true, keepAliveIntervalSeconds, autoKeepAlive);
    }

    private MqttClient mqttClient(
            String clientId,
            boolean cleanSession,
            int keepAliveIntervalSeconds,
            boolean autoKeepAlive) {
        MqttClientOptions options = new MqttClientOptions()
                .setAutoGeneratedClientId(false)
                .setClientId(clientId)
                .setCleanSession(cleanSession)
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
    private record TestBrokerRuntimeConfig(int offlineQueueCapacityPerSession) implements BrokerRuntimeConfig {

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
