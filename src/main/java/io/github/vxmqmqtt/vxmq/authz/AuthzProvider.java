package io.github.vxmqmqtt.vxmq.authz;

/**
 * Protocol-facing authorization coordinator.
 */
public interface AuthzProvider {

    AuthzResult authorize(AuthzContext context);
}
