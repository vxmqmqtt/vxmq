package io.github.vxmqmqtt.vxmq.protocol.model;

import java.util.List;

/**
 * Online deliveries and offline queue work produced by one inbound publish.
 */
public record DeliveryPlan(
        List<PublishDelivery> deliveries,
        int queuedMessageCount) {

    public static DeliveryPlan empty() {
        return new DeliveryPlan(List.of(), 0);
    }

    public static DeliveryPlan of(List<PublishDelivery> deliveries, int queuedMessageCount) {
        return new DeliveryPlan(List.copyOf(deliveries), queuedMessageCount);
    }

    public boolean isEmpty() {
        return deliveries.isEmpty() && queuedMessageCount == 0;
    }
}
