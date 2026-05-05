package io.github.vxmqmqtt.vxmq.authz;

/**
 * AuthzAuthorizer used until concrete ACL resources are configured.
 */
public class PermitAllAuthzAuthorizer implements AuthzAuthorizer {

    @Override
    public AuthzResult authorize(AuthzContext context) {
        return AuthzResult.allow();
    }
}
