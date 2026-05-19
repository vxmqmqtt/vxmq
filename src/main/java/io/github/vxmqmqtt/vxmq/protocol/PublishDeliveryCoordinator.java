package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.observability.BrokerEventSink;
import io.github.vxmqmqtt.vxmq.protocol.model.DeliveryPlan;
import io.github.vxmqmqtt.vxmq.protocol.model.MqttPacketSizeEstimator;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishProperties;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.ReplayPubRel;
import io.github.vxmqmqtt.vxmq.protocol.model.ReplayPublish;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumeAction;
import io.github.vxmqmqtt.vxmq.protocol.model.SessionResumePlan;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessage;
import io.github.vxmqmqtt.vxmq.retained.RetainedMessageRegistry;
import io.github.vxmqmqtt.vxmq.routing.MqttTopicSupport;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionBinding;
import io.github.vxmqmqtt.vxmq.routing.SubscriptionRegistry;
import io.github.vxmqmqtt.vxmq.session.ClientSession;
import io.github.vxmqmqtt.vxmq.session.InflightMessage;
import io.github.vxmqmqtt.vxmq.session.OutboundQos2State;
import io.github.vxmqmqtt.vxmq.session.QueuedMessage;
import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ClientConnectionRegistry;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates message fan-out, retained replay, and outbound QoS delivery state.
 */
final class PublishDeliveryCoordinator {

    private final SessionRegistry sessionRegistry;
    private final RetainedMessageRegistry retainedMessageRegistry;
    private final SubscriptionRegistry subscriptionRegistry;
    private final MqttTopicSupport mqttTopicSupport;
    private final ClientConnectionRegistry connectionRegistry;
    private final BrokerEventSink brokerEventSink;
    private final SessionLifecycleCoordinator sessionLifecycle;
    private final ProtocolDiagnostics diagnostics;
    private final Clock clock;

    PublishDeliveryCoordinator(
            SessionRegistry sessionRegistry,
            RetainedMessageRegistry retainedMessageRegistry,
            SubscriptionRegistry subscriptionRegistry,
            MqttTopicSupport mqttTopicSupport,
            ClientConnectionRegistry connectionRegistry,
            BrokerEventSink brokerEventSink,
            SessionLifecycleCoordinator sessionLifecycle,
            ProtocolDiagnostics diagnostics,
            Clock clock) {
        this.sessionRegistry = sessionRegistry;
        this.retainedMessageRegistry = retainedMessageRegistry;
        this.subscriptionRegistry = subscriptionRegistry;
        this.mqttTopicSupport = mqttTopicSupport;
        this.connectionRegistry = connectionRegistry;
        this.brokerEventSink = brokerEventSink;
        this.sessionLifecycle = sessionLifecycle;
        this.diagnostics = diagnostics;
        this.clock = clock;
    }

    PublishRoutingResult routePublish(ClientConnection connection, PublishRequest request) {
        Instant now = clock.instant();
        updateRetainedMessageIfRequested(request, now);
        if (request.properties().messageExpiry().isExpired(now)) {
            brokerEventSink.messageRouted(connection, request.topicName(), 0);
            return new PublishRoutingResult(List.of(), 0);
        }

        sessionLifecycle.clearExpiredSessionRoutingBindings(now);
        List<PublishDelivery> deliveries = new ArrayList<>();
        int queuedMessageCount = 0;
        for (SubscriptionBinding binding : subscriptionRegistry.match(request.topicName())) {
            if (binding.noLocal() && binding.clientId().equals(connection.effectiveClientId())) {
                continue;
            }
            MqttQoS deliveryQos = grantedDeliveryQos(request.qos(), binding.grantedQos());
            boolean deliveryRetain = binding.retainAsPublished() && request.retain();
            boolean online = connectionRegistry.findActiveConnectionId(binding.clientId()).isPresent();
            if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
                if (online && canSendPublish(binding.clientId(), request.topicName(), request.payload(), deliveryQos,
                        deliveryRetain, false, request.properties(), binding.subscriptionIdentifiers())) {
                    deliveries.add(new PublishDelivery(
                            binding.clientId(),
                            request.topicName(),
                            copyPayload(request.payload()),
                            MqttQoS.AT_MOST_ONCE,
                            deliveryRetain,
                            false,
                            null,
                            false,
                            request.properties(),
                            binding.subscriptionIdentifiers()));
                }
                continue;
            }

            if (online) {
                if (!canSendPublish(binding.clientId(), request.topicName(), request.payload(), deliveryQos,
                        deliveryRetain, false, request.properties(), binding.subscriptionIdentifiers())) {
                    continue;
                }
                InflightMessage inflightMessage = sessionRegistry.createInflightMessage(
                                binding.clientId(),
                                request.topicName(),
                                request.payload(),
                                deliveryQos,
                                deliveryRetain,
                                false,
                                false,
                                request.properties(),
                                binding.subscriptionIdentifiers())
                        .orElse(null);
                if (inflightMessage == null) {
                    sessionRegistry.enqueuePendingOutboundMessage(binding.clientId(), new QueuedMessage(
                            request.topicName(),
                            copyPayload(request.payload()),
                            deliveryQos,
                            deliveryRetain,
                            false,
                            request.properties(),
                            binding.subscriptionIdentifiers()));
                } else {
                    deliveries.add(toPublishDelivery(binding.clientId(), inflightMessage));
                }
                continue;
            }

            ClientSession session = sessionRegistry.find(binding.clientId()).orElse(null);
            if (session != null && session.persistent()) {
                sessionRegistry.enqueueOfflineMessage(binding.clientId(), new QueuedMessage(
                        request.topicName(),
                        copyPayload(request.payload()),
                        deliveryQos,
                        deliveryRetain,
                        false,
                        request.properties(),
                        binding.subscriptionIdentifiers()));
                queuedMessageCount++;
            }
        }

        int matchedClients = deliveries.size() + queuedMessageCount;
        brokerEventSink.messageRouted(connection, request.topicName(), matchedClients);
        return new PublishRoutingResult(deliveries, queuedMessageCount);
    }

    PublishRoutingResult routeServerPublish(ClientConnection connection, PublishRequest request) {
        if (!mqttTopicSupport.isValidTopicName(request.topicName())) {
            diagnostics.publishRejected(connection, request, "TOPIC_NAME_INVALID", MqttDisconnectReasonCode.TOPIC_NAME_INVALID);
            return new PublishRoutingResult(List.of(), 0);
        }
        return routePublish(connection, request);
    }

    SessionResumePlan handleSessionResume(ClientConnection connection) {
        if (connection.effectiveClientId() == null) {
            return SessionResumePlan.empty();
        }

        ClientSession session = sessionRegistry.find(connection.effectiveClientId()).orElse(null);
        if (session == null || !connection.connectionId().equals(session.connectionId())) {
            return SessionResumePlan.empty();
        }

        Instant now = clock.instant();
        List<InflightMessage> drainedMessages = drainQueuedMessagesForResume(connection.effectiveClientId(), now);
        List<Integer> drainedPacketIds = drainedMessages.stream()
                .map(InflightMessage::packetId)
                .toList();
        List<SessionResumeAction> actions = new ArrayList<>(drainedMessages.stream()
                .map(inflightMessage -> new ReplayPublish(toPublishDelivery(connection.effectiveClientId(), inflightMessage)))
                .toList());
        for (InflightMessage inflightMessage : sessionRegistry.outboundQos2InflightMessages(connection.effectiveClientId())) {
            if (drainedPacketIds.contains(inflightMessage.packetId())) {
                continue;
            }
            if (inflightMessage.qos2State() == OutboundQos2State.PUBREL_SENT) {
                actions.add(new ReplayPubRel(inflightMessage.packetId()));
            } else if (inflightMessage.properties().messageExpiry().isExpired(now)) {
                sessionRegistry.completeOutboundQos2(connection.effectiveClientId(), inflightMessage.packetId());
            } else if (!canSendPublish(connection.effectiveClientId(), inflightMessage)) {
                clearOutboundInflight(connection.effectiveClientId(), inflightMessage);
            } else {
                actions.add(new ReplayPublish(toPublishDelivery(connection.effectiveClientId(), inflightMessage)));
            }
        }
        drainPendingInflight(connection.effectiveClientId(), now)
                .stream()
                .map(inflightMessage -> new ReplayPublish(toPublishDelivery(connection.effectiveClientId(), inflightMessage)))
                .forEach(actions::add);
        return actions.isEmpty() ? SessionResumePlan.empty() : new SessionResumePlan(actions);
    }

    DeliveryPlan drainPendingDeliveries(String clientId) {
        List<PublishDelivery> deliveries = drainPendingInflight(clientId, clock.instant())
                .stream()
                .map(inflightMessage -> toPublishDelivery(clientId, inflightMessage))
                .toList();
        return deliveries.isEmpty() ? DeliveryPlan.empty() : DeliveryPlan.of(deliveries, 0);
    }

    boolean shouldReplayRetained(RetainedHandlingPolicy retainHandling, boolean subscriptionAlreadyExisted) {
        return switch (retainHandling) {
            case SEND_AT_SUBSCRIBE -> true;
            case SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS -> !subscriptionAlreadyExisted;
            case DONT_SEND_AT_SUBSCRIBE -> false;
        };
    }

    List<PublishDelivery> buildRetainedDeliveries(SubscriptionBinding subscriptionBinding) {
        List<PublishDelivery> deliveries = new ArrayList<>();
        Instant now = clock.instant();
        for (RetainedMessage retainedMessage : retainedMessageRegistry.findMatching(subscriptionBinding.topicFilter())) {
            if (retainedMessage.properties().messageExpiry().isExpired(now)) {
                retainedMessageRegistry.removeRetained(retainedMessage.topicName());
                continue;
            }
            MqttQoS deliveryQos = grantedDeliveryQos(retainedMessage.qos().value(), subscriptionBinding.grantedQos());
            if (deliveryQos == MqttQoS.AT_MOST_ONCE) {
                if (!canSendPublish(subscriptionBinding.clientId(), retainedMessage.topicName(),
                        retainedMessage.payloadCopy(), MqttQoS.AT_MOST_ONCE, true, false,
                        retainedMessage.properties(), subscriptionBinding.subscriptionIdentifiers())) {
                    continue;
                }
                deliveries.add(new PublishDelivery(
                        subscriptionBinding.clientId(),
                        retainedMessage.topicName(),
                        retainedMessage.payloadCopy(),
                        MqttQoS.AT_MOST_ONCE,
                        true,
                        false,
                        null,
                        false,
                        retainedMessage.properties(),
                        subscriptionBinding.subscriptionIdentifiers()));
                continue;
            }

            if (!canSendPublish(subscriptionBinding.clientId(), retainedMessage.topicName(),
                    retainedMessage.payloadCopy(), deliveryQos, true, false,
                    retainedMessage.properties(), subscriptionBinding.subscriptionIdentifiers())) {
                continue;
            }
            InflightMessage inflightMessage = sessionRegistry.createInflightMessage(
                            subscriptionBinding.clientId(),
                            retainedMessage.topicName(),
                            retainedMessage.payloadCopy(),
                            deliveryQos,
                            true,
                            false,
                            false,
                            retainedMessage.properties(),
                            subscriptionBinding.subscriptionIdentifiers())
                    .orElse(null);
            if (inflightMessage == null) {
                sessionRegistry.enqueuePendingOutboundMessage(subscriptionBinding.clientId(), new QueuedMessage(
                        retainedMessage.topicName(),
                        retainedMessage.payloadCopy(),
                        deliveryQos,
                        true,
                        false,
                        retainedMessage.properties(),
                        subscriptionBinding.subscriptionIdentifiers()));
            } else {
                deliveries.add(toPublishDelivery(subscriptionBinding.clientId(), inflightMessage));
            }
        }
        return deliveries;
    }

    private void updateRetainedMessageIfRequested(PublishRequest request, Instant now) {
        if (!request.retain()) {
            return;
        }

        if (request.payloadSize() == 0) {
            retainedMessageRegistry.removeRetained(request.topicName());
            return;
        }

        if (request.properties().messageExpiry().isExpired(now)) {
            retainedMessageRegistry.removeRetained(request.topicName());
            return;
        }

        retainedMessageRegistry.putRetained(
                request.topicName(),
                request.payload(),
                grantedDeliveryQos(request.qos(), MqttQoS.EXACTLY_ONCE),
                request.properties());
    }

    private List<InflightMessage> drainQueuedMessagesForResume(String clientId, Instant now) {
        List<InflightMessage> deliverable = new ArrayList<>();
        while (true) {
            List<InflightMessage> drained = sessionRegistry.drainQueuedMessages(clientId, now);
            if (drained.isEmpty()) {
                return deliverable;
            }
            for (InflightMessage inflightMessage : drained) {
                if (canSendPublish(clientId, inflightMessage)) {
                    deliverable.add(inflightMessage);
                } else {
                    clearOutboundInflight(clientId, inflightMessage);
                }
            }
        }
    }

    private List<InflightMessage> drainPendingInflight(String clientId, Instant now) {
        List<InflightMessage> deliverable = new ArrayList<>();
        while (true) {
            List<InflightMessage> drained = sessionRegistry.drainPendingOutboundMessages(clientId, now);
            if (drained.isEmpty()) {
                return deliverable;
            }
            for (InflightMessage inflightMessage : drained) {
                if (canSendPublish(clientId, inflightMessage)) {
                    deliverable.add(inflightMessage);
                } else {
                    clearOutboundInflight(clientId, inflightMessage);
                }
            }
        }
    }

    private PublishDelivery toPublishDelivery(String clientId, InflightMessage inflightMessage) {
        return new PublishDelivery(
                clientId,
                inflightMessage.topicName(),
                copyPayload(inflightMessage.payload()),
                inflightMessage.qos(),
                inflightMessage.retain(),
                inflightMessage.duplicate(),
                inflightMessage.packetId(),
                inflightMessage.fromOfflineQueue(),
                inflightMessage.properties(),
                inflightMessage.subscriptionIdentifiers());
    }

    private boolean canSendPublish(String clientId, InflightMessage inflightMessage) {
        return canSendPublish(
                clientId,
                inflightMessage.topicName(),
                inflightMessage.payload(),
                inflightMessage.qos(),
                inflightMessage.retain(),
                inflightMessage.duplicate(),
                inflightMessage.properties(),
                inflightMessage.subscriptionIdentifiers());
    }

    private boolean canSendPublish(
            String clientId,
            String topicName,
            byte[] payload,
            MqttQoS qos,
            boolean retain,
            boolean duplicate,
            PublishProperties properties,
            List<Integer> subscriptionIdentifiers) {
        ClientSession session = sessionRegistry.find(clientId).orElse(null);
        if (session == null) {
            return true;
        }
        int propertiesSize = MqttPacketSizeEstimator.publishPropertiesSize(properties)
                + MqttPacketSizeEstimator.subscriptionIdentifiersPropertiesSize(subscriptionIdentifiers);
        int packetSize = MqttPacketSizeEstimator.publishPacketSize(
                topicName,
                payload == null ? 0 : payload.length,
                qos.value(),
                propertiesSize);
        return packetSize <= session.maximumPacketSize();
    }

    private void clearOutboundInflight(String clientId, InflightMessage inflightMessage) {
        if (inflightMessage.qos() == MqttQoS.AT_LEAST_ONCE) {
            sessionRegistry.acknowledge(clientId, inflightMessage.packetId());
            return;
        }
        if (inflightMessage.qos() == MqttQoS.EXACTLY_ONCE) {
            sessionRegistry.completeOutboundQos2(clientId, inflightMessage.packetId());
        }
    }

    private MqttQoS grantedDeliveryQos(int publishQos, MqttQoS subscriptionQos) {
        int value = Math.min(publishQos, subscriptionQos.value());
        if (value <= 0) {
            return MqttQoS.AT_MOST_ONCE;
        }
        return value == 1 ? MqttQoS.AT_LEAST_ONCE : MqttQoS.EXACTLY_ONCE;
    }

    private byte[] copyPayload(byte[] payload) {
        return payload == null ? null : payload.clone();
    }

    record PublishRoutingResult(List<PublishDelivery> deliveries, int queuedMessageCount) {
    }
}
