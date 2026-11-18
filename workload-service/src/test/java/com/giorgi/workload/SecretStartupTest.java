package com.giorgi.workload;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;

class SecretStartupTest {

    private Throwable startupFailure(String... arguments) {
        SpringApplicationBuilder application = new SpringApplicationBuilder(WorkloadServiceApplication.class);

        Throwable thrown = Assertions.assertThrows(Exception.class, () -> application.run(arguments).close());
        return NestedExceptionUtils.getMostSpecificCause(thrown);
    }

    @Test
    @DisplayName("a secret under 32 bytes stops the startup and names the property and the variable")
    void failsStartupWithShortSecret() {
        Throwable cause = startupFailure("--spring.profiles.active=test", "--server.port=0",
                "--workload.security.jwt-secret=too-short");

        Assertions.assertInstanceOf(IllegalStateException.class, cause);
        Assertions.assertEquals("workload.security.jwt-secret must be at least 32 bytes: set the "
                + "WORKLOAD_JWT_SECRET environment variable to a random value of that length", cause.getMessage());
    }

    @Test
    @DisplayName("a profile that defines no secret stops the startup")
    void failsStartupWithoutAnySecret() {
        Throwable cause = startupFailure("--spring.profiles.active=nosecret", "--server.port=0",
                "--eureka.client.enabled=false");

        Assertions.assertInstanceOf(IllegalStateException.class, cause);
        Assertions.assertTrue(cause.getMessage().startsWith("workload.security.jwt-secret must be at least 32"),
                cause.getMessage());
    }

    @Test
    @DisplayName("the docker profile without WORKLOAD_JWT_SECRET stops the startup")
    void failsStartupOfDockerProfileWithoutVariable() {
        Assumptions.assumeTrue(System.getenv("WORKLOAD_JWT_SECRET") == null,
                "WORKLOAD_JWT_SECRET is set on this machine");

        Throwable cause = startupFailure("--spring.profiles.active=docker", "--server.port=0",
                "--eureka.client.enabled=false");

        Assertions.assertInstanceOf(IllegalStateException.class, cause);
        Assertions.assertEquals("The docker profile has no default token secret: set the WORKLOAD_JWT_SECRET "
                + "environment variable to a random value of at least 32 bytes", cause.getMessage());
    }

    @Test
    @DisplayName("the docker profile without the broker variables stops the startup")
    void failsStartupOfDockerProfileWithoutBrokerVariables() {
        Assumptions.assumeTrue(System.getenv("ARTEMIS_URL") == null, "ARTEMIS_URL is set on this machine");

        Throwable cause = startupFailure("--spring.profiles.active=docker", "--server.port=0",
                "--eureka.client.enabled=false", "--WORKLOAD_JWT_SECRET=a-real-secret-from-the-vault-0123456789");

        Assertions.assertInstanceOf(IllegalStateException.class, cause);
        Assertions.assertEquals("The docker profile has no default broker setting: set the ARTEMIS_URL "
                + "environment variable", cause.getMessage());
    }
}
