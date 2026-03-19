package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

public record SubscriptionRequest(List<SubscriptionItem> items) {
}
