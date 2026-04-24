package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Work that must be resent after a persistent subscriber session reconnects.
 */
public record SessionResumeResult(
        List<PublishDelivery> deliveries,
        List<Integer> qos2PubRelPacketIds) {

    public static SessionResumeResult empty() {
        return new SessionResumeResult(List.of(), List.of());
    }
}
