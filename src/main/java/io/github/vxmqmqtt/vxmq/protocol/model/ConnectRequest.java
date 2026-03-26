package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Broker-facing view of a CONNECT packet.
 */
public record ConnectRequest(
        String requestedClientId,
        String protocolName,
        int protocolVersion,
        boolean cleanSession,
        boolean cleanStart,
        Long sessionExpiryIntervalSeconds,
        String username,
        boolean passwordPresent) {

    /**
     * Returns whether the request uses MQTT 3.1.1.
     */
    public boolean isMqtt311() {
        return protocolVersion == 4;
    }

    /**
     * Returns whether the request uses MQTT 5.
     */
    public boolean isMqtt5() {
        return protocolVersion == 5;
    }

    /**
     * Returns whether this CONNECT requires discarding any previous session state first.
     */
    public boolean startsFreshSession() {
        if (isMqtt311()) {
            return cleanSession;
        }
        if (isMqtt5()) {
            return cleanStart;
        }
        return true;
    }

    /**
     * Returns the MQTT 5 session expiry interval, defaulting to zero when absent.
     */
    public long mqtt5SessionExpiryIntervalSeconds() {
        return sessionExpiryIntervalSeconds == null ? 0L : sessionExpiryIntervalSeconds;
    }

    /**
     * Returns whether the session should survive connection loss.
     */
    public boolean retainsSessionOnDisconnect() {
        if (isMqtt311()) {
            return !cleanSession;
        }
        if (isMqtt5()) {
            return mqtt5SessionExpiryIntervalSeconds() > 0;
        }
        return false;
    }
}
