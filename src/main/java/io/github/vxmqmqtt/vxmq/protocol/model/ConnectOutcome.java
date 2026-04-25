package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Protocol result of one CONNECT attempt.
 */
public record ConnectOutcome(
        ConnectResponse response,
        ConnectionTakeoverPlan takeoverPlan) {

    public static ConnectOutcome accepted(
            AcceptedConnectResponse response,
            ConnectionTakeoverPlan takeoverPlan) {
        return new ConnectOutcome(response, takeoverPlan);
    }

    public static ConnectOutcome rejected(RejectedConnectResponse response) {
        return new ConnectOutcome(response, ConnectionTakeoverPlan.none());
    }
}
