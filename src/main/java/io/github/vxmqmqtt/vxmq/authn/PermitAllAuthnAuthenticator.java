package io.github.vxmqmqtt.vxmq.authn;

/**
 * AuthnAuthenticator used when no authentication resources are enabled.
 */
public class PermitAllAuthnAuthenticator implements AuthnAuthenticator {

    @Override
    public AuthnResult authenticate(AuthnContext context) {
        return AuthnResult.allow(context.request().username());
    }
}
