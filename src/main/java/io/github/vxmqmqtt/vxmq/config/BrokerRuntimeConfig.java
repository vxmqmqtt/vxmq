package io.github.vxmqmqtt.vxmq.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "vxmq.broker")
public interface BrokerRuntimeConfig {

    @WithDefault("true")
    boolean enabled();

    @WithDefault("0.0.0.0")
    String host();

    @WithDefault("1883")
    int port();

    @WithDefault("268435455")
    int maxMessageSize();

    @WithDefault("10")
    int timeoutOnConnectSeconds();
}
