package io.github.vxmqmqtt.vxmq.protocol;

import io.github.vxmqmqtt.vxmq.protocol.model.PublishDelivery;
import io.github.vxmqmqtt.vxmq.protocol.model.PublishRequest;
import io.github.vxmqmqtt.vxmq.protocol.model.WillMessage;
import io.github.vxmqmqtt.vxmq.session.SessionRegistry;
import io.github.vxmqmqtt.vxmq.transport.ClientConnection;
import io.github.vxmqmqtt.vxmq.transport.ConnectionState;
import java.util.List;

/**
 * Owns MQTT Will lifecycle decisions after disconnect and socket close events.
 */
final class WillService {

    private final SessionRegistry sessionRegistry;
    private final PublishDeliveryCoordinator publishDeliveryCoordinator;

    WillService(
            SessionRegistry sessionRegistry,
            PublishDeliveryCoordinator publishDeliveryCoordinator) {
        this.sessionRegistry = sessionRegistry;
        this.publishDeliveryCoordinator = publishDeliveryCoordinator;
    }

    void discardForDisconnect(ClientConnection connection) {
        if (connection.effectiveClientId() != null) {
            sessionRegistry.discardWillMessage(connection.effectiveClientId(), connection.connectionId());
        }
        connection.clearWillMessage();
    }

    WillPublishResult publishOnAbnormalClose(ClientConnection connection) {
        if (!shouldPublishWill(connection)) {
            return WillPublishResult.none();
        }
        WillMessage willMessage = connection.takeWillMessage();
        if (willMessage == null) {
            return WillPublishResult.none();
        }

        if (connection.effectiveClientId() != null) {
            sessionRegistry.discardWillMessage(connection.effectiveClientId(), connection.connectionId());
        }

        PublishDeliveryCoordinator.PublishRoutingResult routingResult =
                publishDeliveryCoordinator.routeServerPublish(connection, new PublishRequest(
                        willMessage.topicName(),
                        0,
                        willMessage.qos().value(),
                        willMessage.retain(),
                        false,
                        willMessage.payloadCopy(),
                        willMessage.properties()));
        return new WillPublishResult(routingResult.deliveries(), true);
    }

    private boolean shouldPublishWill(ClientConnection connection) {
        return connection.effectiveClientId() != null && connection.state() != ConnectionState.DISCONNECTING;
    }

    record WillPublishResult(List<PublishDelivery> deliveries, boolean willPublished) {

        private static WillPublishResult none() {
            return new WillPublishResult(List.of(), false);
        }
    }
}
