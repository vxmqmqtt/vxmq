package io.github.vxmqmqtt.vxmq.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApplicationSecurityDefaultsTest {

    // Verifies that production defaults do not make configured authn/authz resources inherit fail-open no-match.
    @Test
    void shouldNotConfigureProductionAuthNoMatchAllowByDefault() throws IOException {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertFalse(applicationYaml.contains("""
                    authn:
                      no-match: allow
                """));
        assertFalse(applicationYaml.contains("""
                    authz:
                      no-match: allow
                """));
    }
}
