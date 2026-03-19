package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectDecision;
import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.SubscriptionRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeResult;
import io.github.vxmqmqtt.vxmq.protocol.model.UnsubscribeRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

public interface ProtocolEngine {

    ConnectDecision handleConnect(ClientConnection connection, ConnectRequest request);

    SubscribeResult handleSubscribe(ClientConnection connection, SubscriptionRequest request);

    UnsubscribeResult handleUnsubscribe(ClientConnection connection, UnsubscribeRequest request);

    PublishResult handlePublish(ClientConnection connection, PublishRequest request);

    void handleDisconnect(ClientConnection connection);

    void handleConnectionClosed(ClientConnection connection);
}
