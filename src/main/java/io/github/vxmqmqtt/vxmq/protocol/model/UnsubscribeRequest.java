package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

public record UnsubscribeRequest(List<String> topicFilters) {
}
