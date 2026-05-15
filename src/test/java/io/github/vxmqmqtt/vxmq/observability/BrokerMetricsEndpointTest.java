package io.github.vxmqmqtt.vxmq.observability;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BrokerMetricsEndpointTest {

    @Test
    void shouldExposeBrokerMetricsThroughPrometheusEndpoint() {
        String metrics = get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertContains(metrics,
                "vxmq_connections_active",
                "vxmq_sessions_total",
                "vxmq_broker_ready",
                "vxmq_broker_live",
                "vxmq_broker_transport_state",
                "vxmq_transport_starts_total",
                "vxmq_transport_stops_total",
                "vxmq_connections_accepted_total",
                "vxmq_subscriptions_added_total",
                "vxmq_subscriptions_removed_total",
                "vxmq_messages_routed_total",
                "vxmq_message_delivery_matches_total",
                "vxmq_protocol_warnings_total");
    }

    private static void assertContains(String body, String... expectedFragments) {
        for (String expectedFragment : expectedFragments) {
            org.hamcrest.MatcherAssert.assertThat(body, containsString(expectedFragment));
        }
    }
}
