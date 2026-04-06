package io.github.vxmqmqtt.vxmq.protocol.model;

/**
 * Broker-facing view of a CONNECT packet.
 */
public record ConnectRequest(
        String requestedClientId,
        String protocolName,
        int protocolVersion,
        Boolean cleanSession,
        Boolean cleanStart,
        Long sessionExpiryIntervalSeconds,
        String username,
        boolean passwordPresent) {

    public ConnectRequest {
        validateProtocolSpecificFlags(protocolVersion, cleanSession, cleanStart);
    }

    /**
     * Creates a MQTT 3.1.1 CONNECT request.
     */
    public static ConnectRequest mqtt311(
            String requestedClientId,
            String protocolName,
            boolean cleanSession,
            String username,
            boolean passwordPresent) {
        return new ConnectRequest(
                requestedClientId,
                protocolName,
                4,
                cleanSession,
                null,
                null,
                username,
                passwordPresent);
    }

    /**
     * Creates a MQTT 5 CONNECT request.
     */
    public static ConnectRequest mqtt5(
            String requestedClientId,
            String protocolName,
            boolean cleanStart,
            Long sessionExpiryIntervalSeconds,
            String username,
            boolean passwordPresent) {
        return new ConnectRequest(
                requestedClientId,
                protocolName,
                5,
                null,
                cleanStart,
                sessionExpiryIntervalSeconds,
                username,
                passwordPresent);
    }

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
     * Returns the MQTT 3.1.1 cleanSession flag.
     */
    public boolean mqtt311CleanSession() {
        if (!isMqtt311() || cleanSession == null) {
            throw new IllegalStateException("cleanSession is only available for MQTT 3.1.1 CONNECT requests");
        }
        return cleanSession;
    }

    /**
     * Returns the MQTT 5 cleanStart flag.
     */
    public boolean mqtt5CleanStart() {
        if (!isMqtt5() || cleanStart == null) {
            throw new IllegalStateException("cleanStart is only available for MQTT 5 CONNECT requests");
        }
        return cleanStart;
    }

    /**
     * Returns whether this CONNECT requires discarding any previous session state first.
     */
    public boolean startsFreshSession() {
        if (isMqtt311()) {
            return mqtt311CleanSession();
        }
        if (isMqtt5()) {
            return mqtt5CleanStart();
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
            return !mqtt311CleanSession();
        }
        if (isMqtt5()) {
            return mqtt5SessionExpiryIntervalSeconds() > 0;
        }
        return false;
    }

    private static void validateProtocolSpecificFlags(int protocolVersion, Boolean cleanSession, Boolean cleanStart) {
        if (protocolVersion == 4) {
            if (cleanSession == null) {
                throw new IllegalArgumentException("MQTT 3.1.1 CONNECT requests must include cleanSession");
            }
            if (cleanStart != null) {
                throw new IllegalArgumentException("MQTT 3.1.1 CONNECT requests must not include cleanStart");
            }
        }

        if (protocolVersion == 5) {
            if (cleanStart == null) {
                throw new IllegalArgumentException("MQTT 5 CONNECT requests must include cleanStart");
            }
            if (cleanSession != null) {
                throw new IllegalArgumentException("MQTT 5 CONNECT requests must not include cleanSession");
            }
        }
    }
}
