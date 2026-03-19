package io.github.vxmqmqtt.vxmq.session;

import java.util.Optional;

public interface SessionRegistry {

    ClientSession bindConnection(String clientId, String connectionId);

    void unbindConnection(String clientId, String connectionId);

    void addSubscription(String clientId, String topicFilter);

    boolean removeSubscription(String clientId, String topicFilter);

    Optional<ClientSession> find(String clientId);
}
