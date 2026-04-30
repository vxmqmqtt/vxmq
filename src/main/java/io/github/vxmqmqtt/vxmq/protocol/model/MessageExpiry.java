package io.github.vxmqmqtt.vxmq.protocol.model;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Broker-side expiry deadline derived from MQTT 5 Message Expiry Interval.
 */
public record MessageExpiry(Instant expiresAt) {

    private static final MessageExpiry NONE = new MessageExpiry(null);

    public static MessageExpiry none() {
        return NONE;
    }

    public static MessageExpiry fromIntervalSeconds(long intervalSeconds, Instant receivedAt) {
        if (receivedAt == null) {
            throw new IllegalArgumentException("receivedAt must not be null");
        }
        if (intervalSeconds < 0L || intervalSeconds > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("intervalSeconds must fit MQTT 5 Four Byte Integer");
        }
        return new MessageExpiry(receivedAt.plusSeconds(intervalSeconds));
    }

    public boolean isEmpty() {
        return expiresAt == null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public OptionalLong remainingIntervalSeconds(Instant now) {
        if (expiresAt == null || isExpired(now)) {
            return OptionalLong.empty();
        }
        long remaining = Duration.between(now, expiresAt).getSeconds();
        if (remaining <= 0L) {
            return OptionalLong.of(1L);
        }
        return OptionalLong.of(Math.min(remaining, 0xFFFF_FFFFL));
    }
}
