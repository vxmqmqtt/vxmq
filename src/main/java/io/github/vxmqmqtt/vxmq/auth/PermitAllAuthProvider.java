package io.github.vxmqmqtt.vxmq.auth;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Temporary auth provider used during early milestones to accept every client.
 */
@ApplicationScoped
public class PermitAllAuthProvider implements AuthProvider {

    @Override
    public boolean allowConnect(ClientConnection connection, ConnectRequest request) {
        return true;
    }
}
