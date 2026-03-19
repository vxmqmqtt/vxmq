package io.github.vxmqmqtt.vxmq.bootstrap;

import io.github.vxmqmqtt.vxmq.config.BrokerRuntimeConfig;
import io.github.vxmqmqtt.vxmq.transport.BrokerTransport;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

/**
 * Starts and stops the broker transport with the Quarkus application lifecycle.
 */
@ApplicationScoped
public class BrokerBootstrap {

    private static final Logger LOG = Logger.getLogger(BrokerBootstrap.class);

    private final BrokerRuntimeConfig brokerRuntimeConfig;
    private final BrokerTransport brokerTransport;

    public BrokerBootstrap(BrokerRuntimeConfig brokerRuntimeConfig, BrokerTransport brokerTransport) {
        this.brokerRuntimeConfig = brokerRuntimeConfig;
        this.brokerTransport = brokerTransport;
    }

    /**
     * Boots the transport only when the broker is enabled in configuration.
     */
    void onStart(@Observes StartupEvent event) {
        if (!brokerRuntimeConfig.enabled()) {
            LOG.info("VXMQ MQTT broker is disabled by configuration");
            return;
        }
        LOG.info("Starting VXMQ MQTT broker");
        brokerTransport.start().await().indefinitely();
    }

    /**
     * Stops the transport before the Quarkus process exits.
     */
    void onStop(@Observes ShutdownEvent event) {
        if (!brokerRuntimeConfig.enabled()) {
            return;
        }
        LOG.info("Stopping VXMQ MQTT broker");
        brokerTransport.stop().await().indefinitely();
    }
}
