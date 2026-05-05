package io.github.vxmqmqtt.vxmq.authn;

/**
 * Authenticates one MQTT CONNECT attempt.
 */
public interface AuthnAuthenticator {

    AuthnResult authenticate(AuthnContext context);
}
