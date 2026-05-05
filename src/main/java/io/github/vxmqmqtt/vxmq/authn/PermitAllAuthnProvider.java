package io.github.vxmqmqtt.vxmq.authn;

import io.github.vxmqmqtt.vxmq.protocol.model.ConnectRequest;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

/**
 * Temporary authn provider used during early milestones to accept every client.
 */
public class PermitAllAuthnProvider implements AuthnProvider {

    @Override
    public AuthnResult authenticate(ClientConnection connection, ConnectRequest request) {
        return AuthnResult.allow(request.username());
    }
}
