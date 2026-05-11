package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.DeliveryPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPubRelOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.InboundPublishOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.OutboundPubRecOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumePlan;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeOutcome;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeAck;
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
    ConnectOutcome handleConnect(ClientConnection connection, ConnectRequest request);

    /**
     * Processes a SUBSCRIBE packet and returns per-filter SUBACK outcomes.
     */
    SubscribeOutcome handleSubscribe(ClientConnection connection, SubscriptionRequest request);

    /**
     * Processes an UNSUBSCRIBE packet and returns per-filter UNSUBACK outcomes.
     */
    UnsubscribeAck handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request);

    /**
     * Processes an inbound PUBLISH and returns the transport-facing protocol outcome.
     */
    InboundPublishOutcome handlePublish(ClientConnection connection, PublishRequest request);

    /**
     * Returns queued deliveries that should be resumed after a session reconnect succeeds.
     */
    SessionResumePlan handleSessionResume(ClientConnection connection);

    /**
     * Completes one QoS 1 outbound delivery after the subscriber acknowledges it.
     */
    DeliveryPlan handlePubAck(ClientConnection connection, int packetId);

    /**
     * Completes one inbound QoS 2 publish transaction after receiving PUBREL from a publisher.
     */
    InboundPubRelOutcome handlePubRel(ClientConnection connection, int packetId);

    /**
     * Advances one outbound QoS 2 delivery after receiving PUBREC from a subscriber.
     */
    OutboundPubRecOutcome handlePubRec(ClientConnection connection, int packetId);

    /**
     * Completes one outbound QoS 2 delivery after receiving PUBCOMP from a subscriber.
     */
    DeliveryPlan handlePubComp(ClientConnection connection, int packetId);

    /**
     * Handles an explicit MQTT DISCONNECT received from the client.
     */
    void handleDisconnect(ClientConnection connection);

    /**
     * Cleans up connection state after the underlying network channel has closed.
     */
    List<PublishDelivery> handleConnectionClosed(ClientConnection connection);
}
