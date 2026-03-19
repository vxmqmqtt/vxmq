package io.github.vxmqmqtt.vxmq.transport;

import io.smallrye.mutiny.Uni;

/**
 * Starts and stops the MQTT transport implementation.
 */
public interface BrokerTransport {

    /**
     * Starts accepting MQTT connections.
     */
    Uni<Void> start();

    /**
     * Stops accepting MQTT connections and releases transport resources.
     */
    Uni<Void> stop();
}
