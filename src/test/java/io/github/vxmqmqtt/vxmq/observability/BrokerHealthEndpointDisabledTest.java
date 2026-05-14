package io.github.vxmqmqtt.vxmq.observability;

import static io.restassured.RestAssured.get;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BrokerHealthEndpointDisabledTest {

    @Test
    void shouldKeepLivenessUpWhenBrokerIsDisabled() {
        get("/q/health/live")
                .then()
                .statusCode(200);
    }

    @Test
    void shouldReportReadinessDownWhenBrokerIsDisabled() {
        get("/q/health/ready")
                .then()
                .statusCode(503);
    }
}
