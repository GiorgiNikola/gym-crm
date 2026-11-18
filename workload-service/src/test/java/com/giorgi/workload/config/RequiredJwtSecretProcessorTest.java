package com.giorgi.workload.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

class RequiredJwtSecretProcessorTest {

    private final RequiredJwtSecretProcessor processor = new RequiredJwtSecretProcessor();
    private final MockEnvironment environment = new MockEnvironment();

    private void process() {
        processor.postProcessEnvironment(environment, null);
    }

    @Test
    @DisplayName("the docker profile without WORKLOAD_JWT_SECRET stops the application and says so")
    void stopsDockerProfileWithoutSecret() {
        environment.setActiveProfiles("docker");

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, this::process);

        Assertions.assertEquals("The docker profile has no default token secret: set the WORKLOAD_JWT_SECRET "
                + "environment variable to a random value of at least 32 bytes", thrown.getMessage());
    }

    @Test
    @DisplayName("the docker profile with WORKLOAD_JWT_SECRET set starts normally")
    void acceptsDockerProfileWithSecret() {
        environment.setActiveProfiles("docker");
        environment.setProperty("WORKLOAD_JWT_SECRET", "a-real-secret-from-the-vault-0123456789");

        Assertions.assertDoesNotThrow(this::process);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("the local and the test profile bring their own secret, so the variable is optional")
    void leavesOtherProfilesAlone(String profile) {
        environment.setActiveProfiles(profile);

        Assertions.assertDoesNotThrow(this::process);
    }

    @Test
    @DisplayName("no active profile is nothing to check")
    void leavesAProfilelessEnvironmentAlone() {
        Assertions.assertDoesNotThrow(this::process);
    }

    @Test
    @DisplayName("the processor is registered with Spring Boot")
    void isRegisteredAsEnvironmentPostProcessor() {
        List<EnvironmentPostProcessor> processors = SpringFactoriesLoader
                .forDefaultResourceLocation(getClass().getClassLoader())
                .load(EnvironmentPostProcessor.class, (type, implementation, failure) -> {
                });

        Assertions.assertTrue(processors.stream().anyMatch(RequiredJwtSecretProcessor.class::isInstance),
                processors.toString());
    }
}
