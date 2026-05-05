package io.github.vxmqmqtt.vxmq.authn;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

/**
 * Decides whether an incoming MQTT connection is allowed to continue.
 */
public interface AuthnProvider {

    /**
     * Evaluates the CONNECT request before the broker accepts the endpoint.
     */
    AuthnResult authenticate(ClientConnection connection, ConnectRequest request);
}
