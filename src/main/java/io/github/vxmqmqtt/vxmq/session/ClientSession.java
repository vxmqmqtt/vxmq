package io.github.vxmqmqtt.vxmq.session;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientSession {

    private final String clientId;
    private volatile String connectionId;
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();

    public ClientSession(String clientId) {
        this.clientId = clientId;
    }

    public String clientId() {
        return clientId;
    }

    public String connectionId() {
        return connectionId;
    }

    public void bindConnection(String newConnectionId) {
        this.connectionId = newConnectionId;
    }

    public void unbindConnection() {
        this.connectionId = null;
    }

    public Set<String> subscriptions() {
        return subscriptions;
    }
}
