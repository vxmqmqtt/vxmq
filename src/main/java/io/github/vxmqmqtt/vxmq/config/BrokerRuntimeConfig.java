package io.github.vxmqmqtt.vxmq.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration for the embedded MQTT broker transport.
 */
@ConfigMapping(prefix = "vxmq.broker")
public interface BrokerRuntimeConfig {

    /**
     * Enables or disables broker bootstrap at application startup.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Host address used by the MQTT server socket.
     */
    @WithDefault("0.0.0.0")
    String host();

    /**
     * TCP port exposed by the MQTT server socket.
     */
    @WithDefault("1883")
    int port();

    /**
     * Maximum MQTT packet size accepted by the transport layer.
     */
    @WithDefault("268435455")
    int maxMessageSize();

    /**
     * Maximum time allowed for a client to finish the CONNECT handshake.
     */
    @WithDefault("10")
    int timeoutOnConnectSeconds();
}
