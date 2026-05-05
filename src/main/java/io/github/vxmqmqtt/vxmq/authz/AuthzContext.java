package io.github.vxmqmqtt.vxmq.authz;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;

/**
 * Input available to client authorizers for one MQTT operation.
 *
 * The principal is nullable when authentication allowed the client without
 * establishing an application identity, such as permit-all or no-match allow.
 */
public record AuthzContext(
        ClientConnection connection,
        String clientId,
        String principal,
        AuthzAction action,
        String topic) {
}
