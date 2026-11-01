package com.giorgi.gymcrm.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class RequiredJwtSecretProcessorTest {

    private final RequiredJwtSecretProcessor processor = new RequiredJwtSecretProcessor();
    private final MockEnvironment environment = new MockEnvironment();

    private void process() {
        processor.postProcessEnvironment(environment, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stg", "prod"})
    @DisplayName("a deployed profile without JWT_SECRET stops the application and says so")
    void stopsDeployedProfileWithoutSecret(String profile) {
        environment.setActiveProfiles(profile);

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, this::process);

        Assertions.assertEquals("The " + profile + " profile has no default token secret: set the JWT_SECRET "
                + "environment variable to a random value of at least 32 bytes", thrown.getMessage());
    }

    @Test
    @DisplayName("a deployed profile with JWT_SECRET set starts normally")
    void acceptsDeployedProfileWithSecret() {
        environment.setActiveProfiles("prod");
        environment.setProperty("JWT_SECRET", "a-real-secret-from-the-vault-0123456789");

        Assertions.assertDoesNotThrow(this::process);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "standalone"})
    @DisplayName("the development profiles bring their own marked secret, so JWT_SECRET is optional")
    void leavesDevelopmentProfilesAlone(String profile) {
        environment.setActiveProfiles(profile);

        Assertions.assertDoesNotThrow(this::process);
    }

    @Test
    @DisplayName("no active profile is nothing to check")
    void leavesAProfilelessEnvironmentAlone() {
        Assertions.assertDoesNotThrow(this::process);
    }
}
