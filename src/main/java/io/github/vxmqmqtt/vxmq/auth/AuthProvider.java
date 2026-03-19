package io.github.vxmqmqtt.vxmq.auth;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

public interface AuthProvider {

    boolean allowConnect(ClientConnection connection, ConnectRequest request);
}
