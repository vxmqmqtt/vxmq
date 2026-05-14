package io.github.vxmqmqtt.vxmq.observability;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.hasItem;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(BrokerHealthEndpointEnabledTest.EnabledBrokerProfile.class)
class BrokerHealthEndpointEnabledTest {

    @Test
    void shouldReportReadinessAndLivenessUpWhenBrokerIsListening() {
        get("/q/health/live")
                .then()
                .statusCode(200)
                .body("checks.name", hasItem("vxmq-broker-liveness"));

        get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("checks.name", hasItem("vxmq-broker-readiness"));
    }

    public static final class EnabledBrokerProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "vxmq.broker.enabled", "true",
                    "vxmq.broker.host", "127.0.0.1",
                    "vxmq.broker.port", "0");
        }
    }
}
