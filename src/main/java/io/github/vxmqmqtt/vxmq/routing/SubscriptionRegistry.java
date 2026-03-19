package io.github.vxmqmqtt.vxmq.routing;

import java.util.Collection;

public interface SubscriptionRegistry {

    void addSubscription(SubscriptionBinding subscriptionBinding);

    boolean removeSubscription(String clientId, String topicFilter);

    Collection<SubscriptionBinding> match(String topicName);
}
