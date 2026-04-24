package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectDecision;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PubRecResult;
import io.github.vxmqmqtt.vxmq.protocol.model.PubRelResult;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.util.List;

/**
 * Encapsulates broker-side MQTT protocol decisions independently from transport code.
 */
public interface ProtocolEngine {

    /**
     * Validates CONNECT and returns the broker decision to the transport layer.
     */
    ConnectDecision handleConnect(ClientConnection connection, ConnectRequest request);

    /**
     * Processes a SUBSCRIBE packet and returns per-filter SUBACK outcomes.
     */
    SubscribeResult handleSubscribe(ClientConnection connection, SubscriptionRequest request);

    /**
     * Processes an UNSUBSCRIBE packet and returns per-filter UNSUBACK outcomes.
     */
    UnsubscribeResult handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request);

    /**
     * Processes an inbound PUBLISH and returns whether it was accepted, plus any deliveries,
     * acknowledgements, or disconnect requirement produced by protocol handling.
     */
    PublishResult handlePublish(ClientConnection connection, PublishRequest request);

    /**
     * Returns queued deliveries that should be resumed after a session reconnect succeeds.
     */
    SessionResumeResult handleSessionResume(ClientConnection connection);

    /**
     * Completes one QoS 1 outbound delivery after the subscriber acknowledges it.
     */
    void handlePubAck(ClientConnection connection, int packetId);

    /**
     * Completes one inbound QoS 2 publish transaction after receiving PUBREL from a publisher.
     */
    PubRelResult handlePubRel(ClientConnection connection, int packetId);

    /**
     * Advances one outbound QoS 2 delivery after receiving PUBREC from a subscriber.
     */
    PubRecResult handlePubRec(ClientConnection connection, int packetId);

    /**
     * Completes one outbound QoS 2 delivery after receiving PUBCOMP from a subscriber.
     */
    void handlePubComp(ClientConnection connection, int packetId);

    /**
     * Handles an explicit MQTT DISCONNECT received from the client.
     */
    void handleDisconnect(ClientConnection connection);

    /**
     * Cleans up connection state after the underlying network channel has closed.
     */
    List<PublishDelivery> handleConnectionClosed(ClientConnection connection);
}
