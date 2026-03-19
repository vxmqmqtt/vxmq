package io.github.vxmqmqtt.vxmq.auth;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PermitAllAuthProvider implements AuthProvider {

    @Override
    public boolean allowConnect(ClientConnection connection, ConnectRequest request) {
        return true;
    }
}
