package io.github.vxmqmqtt.vxmq.observability;

import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Low-cardinality diagnostic event for broker logs.
 */
public final class BrokerDiagnosticEvent {

    private final String event;
    private final BrokerDiagnosticSeverity severity;
    private final String operation;
    private final String reason;
    private final ClientConnection connection;
    private final String connectionId;
    private final String clientId;
    private final String requestedClientId;
    private final String remote;
    private final Integer protocolVersion;
    private final String mqttReasonCode;
    private final String mqttReturnCode;
    private final String topic;
    private final String topicFilter;
    private final Integer packetId;
    private final Integer qos;
    private final String transportAction;
    private final Boolean sessionPresent;
    private final Boolean willPublished;
    private final Boolean sessionRemoved;
    private final Integer matchedClients;

    private BrokerDiagnosticEvent(Builder builder) {
        event = requireText(builder.event, "event");
        severity = builder.severity == null ? BrokerDiagnosticSeverity.WARN : builder.severity;
        operation = normalize(builder.operation);
        reason = normalize(builder.reason);
        connection = builder.connection;
        connectionId = firstNonBlank(builder.connectionId, connection == null ? null : connection.connectionId());
        clientId = firstNonBlank(
                builder.clientId,
                connection == null ? null : connection.effectiveClientId(),
                connection == null ? null : connection.requestedClientId());
        requestedClientId = firstNonBlank(
                builder.requestedClientId,
                connection == null ? null : connection.requestedClientId());
        remote = firstNonBlank(builder.remote, connection == null ? null : connection.remoteAddress());
        protocolVersion = builder.protocolVersion != null
                ? builder.protocolVersion
                : connection == null ? null : Integer.valueOf(connection.protocolVersion());
        mqttReasonCode = normalize(builder.mqttReasonCode);
        mqttReturnCode = normalize(builder.mqttReturnCode);
        topic = normalize(builder.topic);
        topicFilter = normalize(builder.topicFilter);
        packetId = builder.packetId;
        qos = builder.qos;
        transportAction = normalize(builder.transportAction);
        sessionPresent = builder.sessionPresent;
        willPublished = builder.willPublished;
        sessionRemoved = builder.sessionRemoved;
        matchedClients = builder.matchedClients;
    }

    public static Builder builder(String event) {
        return new Builder(event);
    }

    public BrokerDiagnosticSeverity severity() {
        return severity;
    }

    public ClientConnection connection() {
        return connection;
    }

    public String format() {
        List<String> fields = new ArrayList<>();
        add(fields, "event", event);
        add(fields, "severity", severity);
        add(fields, "operation", operation);
        add(fields, "reason", reason);
        add(fields, "connectionId", connectionId);
        add(fields, "clientId", clientId);
        add(fields, "requestedClientId", requestedClientId);
        add(fields, "remote", remote);
        add(fields, "protocolVersion", protocolVersion);
        add(fields, "mqttReasonCode", mqttReasonCode);
        add(fields, "mqttReturnCode", mqttReturnCode);
        add(fields, "topic", topic);
        add(fields, "topicFilter", topicFilter);
        add(fields, "packetId", packetId);
        add(fields, "qos", qos);
        add(fields, "transportAction", transportAction);
        add(fields, "sessionPresent", sessionPresent);
        add(fields, "willPublished", willPublished);
        add(fields, "sessionRemoved", sessionRemoved);
        add(fields, "matchedClients", matchedClients);
        return String.join(" ", fields);
    }

    private static void add(List<String> fields, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return;
        }
        fields.add(key + "=" + text.replaceAll("\\s+", "_"));
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        return Objects.requireNonNull(normalized, name);
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    public static final class Builder {

        private final String event;
        private BrokerDiagnosticSeverity severity;
        private String operation;
        private String reason;
        private ClientConnection connection;
        private String connectionId;
        private String clientId;
        private String requestedClientId;
        private String remote;
        private Integer protocolVersion;
        private String mqttReasonCode;
        private String mqttReturnCode;
        private String topic;
        private String topicFilter;
        private Integer packetId;
        private Integer qos;
        private String transportAction;
        private Boolean sessionPresent;
        private Boolean willPublished;
        private Boolean sessionRemoved;
        private Integer matchedClients;

        private Builder(String event) {
            this.event = event;
        }

        public Builder severity(BrokerDiagnosticSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder reason(Object reason) {
            this.reason = normalize(reason);
            return this;
        }

        public Builder connection(ClientConnection connection) {
            this.connection = connection;
            return this;
        }

        public Builder connectionId(String connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder requestedClientId(String requestedClientId) {
            this.requestedClientId = requestedClientId;
            return this;
        }

        public Builder remote(String remote) {
            this.remote = remote;
            return this;
        }

        public Builder protocolVersion(Integer protocolVersion) {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Builder mqttReasonCode(Object mqttReasonCode) {
            this.mqttReasonCode = normalize(mqttReasonCode);
            return this;
        }

        public Builder mqttReturnCode(Object mqttReturnCode) {
            this.mqttReturnCode = normalize(mqttReturnCode);
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder topicFilter(String topicFilter) {
            this.topicFilter = topicFilter;
            return this;
        }

        public Builder packetId(Integer packetId) {
            this.packetId = packetId;
            return this;
        }

        public Builder qos(Integer qos) {
            this.qos = qos;
            return this;
        }

        public Builder transportAction(String transportAction) {
            this.transportAction = transportAction;
            return this;
        }

        public Builder sessionPresent(Boolean sessionPresent) {
            this.sessionPresent = sessionPresent;
            return this;
        }

        public Builder willPublished(Boolean willPublished) {
            this.willPublished = willPublished;
            return this;
        }

        public Builder sessionRemoved(Boolean sessionRemoved) {
            this.sessionRemoved = sessionRemoved;
            return this;
        }

        public Builder matchedClients(Integer matchedClients) {
            this.matchedClients = matchedClients;
            return this;
        }

        public BrokerDiagnosticEvent build() {
            return new BrokerDiagnosticEvent(this);
        }
    }
}
