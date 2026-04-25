package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Follow-up connection management work for CONNECT handling.
 */
public record ConnectionTakeoverPlan(String supersededConnectionId) {

    public static ConnectionTakeoverPlan none() {
        return new ConnectionTakeoverPlan(null);
    }

    public static ConnectionTakeoverPlan takeOver(String supersededConnectionId) {
        return new ConnectionTakeoverPlan(supersededConnectionId);
    }

    public boolean requiresTakeover() {
        return supersededConnectionId != null;
    }
}
