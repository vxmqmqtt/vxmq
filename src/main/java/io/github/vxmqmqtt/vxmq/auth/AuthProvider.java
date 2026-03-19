package io.github.vxmqmqtt.vxmq.auth;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

/**
 * Decides whether an incoming MQTT connection is allowed to continue.
 */
public interface AuthProvider {

    /**
     * Evaluates the CONNECT request before the broker accepts the endpoint.
     */
    boolean allowConnect(ClientConnection connection, ConnectRequest request);
}
