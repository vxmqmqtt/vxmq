package io.github.vxmqmqtt.vxmq.protocol.model;

public record PublishRequest(String topicName, int qos, boolean retain, boolean duplicate, byte[] payload) {

    public int payloadSize() {
        return payload == null ? 0 : payload.length;
    }
}
