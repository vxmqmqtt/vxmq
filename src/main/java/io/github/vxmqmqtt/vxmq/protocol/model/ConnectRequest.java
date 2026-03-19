package io.github.vxmqmqtt.vxmq.protocol.model;

public record ConnectRequest(
        String requestedClientId,
        String protocolName,
        int protocolVersion,
        boolean cleanSession,
        String username,
        boolean passwordPresent) {

    public boolean isMqtt311() {
        return protocolVersion == 4;
    }

    public boolean isMqtt5() {
        return protocolVersion == 5;
    }
}
