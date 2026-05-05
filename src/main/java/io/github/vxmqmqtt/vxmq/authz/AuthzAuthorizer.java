package io.github.vxmqmqtt.vxmq.authz;

/**
 * Authorizes one MQTT client operation.
 */
public interface AuthzAuthorizer {

    AuthzResult authorize(AuthzContext context);
}
