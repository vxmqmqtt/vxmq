package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Broker-facing view of one UNSUBSCRIBE packet.
 */
public record UnsubscribeRequest(List<String> topicFilters) {
}
