package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.session.ClientSession;
import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import java.time.Instant;

/**
 * Keeps session truth and derived routing indexes synchronized.
 */
final class SessionLifecycleCoordinator {

    private final SessionRegistry sessionRegistry;
    private final SubscriptionRegistry subscriptionRegistry;

    SessionLifecycleCoordinator(
            SessionRegistry sessionRegistry,
            SubscriptionRegistry subscriptionRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
    }

    void clearRoutingBindings(ClientSession clearedSession) {
        if (clearedSession == null) {
            return;
        }

        for (String topicFilter : clearedSession.subscriptions()) {
            subscriptionRegistry.removeSubscription(clearedSession.clientId(), topicFilter);
        }
    }

    void clearExpiredSessionRoutingBindings(Instant now) {
        for (ClientSession expiredSession : sessionRegistry.removeExpiredSessions(now)) {
            clearRoutingBindings(expiredSession);
        }
    }

    void restoreSessionSubscription(
            String clientId,
            String topicFilter,
            SubscriptionBinding previousSubscription) {
        if (previousSubscription == null) {
            sessionRegistry.removeSubscription(clientId, topicFilter);
            return;
        }
        sessionRegistry.addSubscription(previousSubscription);
    }
}
