package io.github.vxmqmqtt.vxmq.authn;

import java.util.Map;
import java.util.Objects;

/**
 * Static username/password authenticator backed by configuration.
 */
public class StaticPasswordAuthnAuthenticator implements AuthnAuthenticator {

    private final Map<String, String> passwordsByUsername;

    public StaticPasswordAuthnAuthenticator(Map<String, String> passwordsByUsername) {
        this.passwordsByUsername = Map.copyOf(
                Objects.requireNonNull(passwordsByUsername, "passwordsByUsername"));
    }

    @Override
    public AuthnResult authenticate(AuthnContext context) {
        String username = context.request().username();
        if (!passwordsByUsername.containsKey(username)) {
            return AuthnResult.abstain();
        }
        if (!context.request().passwordPresent()) {
            return AuthnResult.deny(
                    AuthnReason.BAD_USERNAME_OR_PASSWORD,
                    "Password is required for configured static username");
        }
        if (passwordsByUsername.get(username).equals(context.request().password())) {
            return AuthnResult.allow(username);
        }
        return AuthnResult.deny(
                AuthnReason.BAD_USERNAME_OR_PASSWORD,
                "Password does not match configured static username");
    }
}
