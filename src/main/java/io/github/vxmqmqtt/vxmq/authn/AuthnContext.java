package io.github.vxmqmqtt.vxmq.authn;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

/**
 * Input available to client authenticators for one CONNECT attempt.
 */
public record AuthnContext(
        ClientConnection connection,
        ConnectRequest request) {
}
