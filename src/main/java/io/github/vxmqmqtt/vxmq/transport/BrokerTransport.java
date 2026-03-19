package io.github.vxmqmqtt.vxmq.transport;

import io.smallrye.mutiny.Uni;

public interface BrokerTransport {

    Uni<Void> start();

    Uni<Void> stop();
}
