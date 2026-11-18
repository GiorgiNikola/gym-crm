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

class RequiredBrokerVariablesProcessorTest {

    private final RequiredBrokerVariablesProcessor processor = new RequiredBrokerVariablesProcessor();
    private final MockEnvironment environment = new MockEnvironment();

    private void process() {
        processor.postProcessEnvironment(environment, null);
    }

    private void setAllBut(String missing) {
        for (String variable : List.of("ARTEMIS_URL", "ARTEMIS_USER", "ARTEMIS_PASSWORD")) {
            if (!variable.equals(missing)) {
                environment.setProperty(variable, "from-the-environment");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARTEMIS_URL", "ARTEMIS_USER", "ARTEMIS_PASSWORD"})
    @DisplayName("the docker profile without one broker variable stops and names that variable")
    void stopsDockerProfileWithoutVariable(String missing) {
        environment.setActiveProfiles("docker");
        setAllBut(missing);

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, this::process);

        Assertions.assertEquals("The docker profile has no default broker setting: set the " + missing
                + " environment variable", thrown.getMessage());
    }

    @Test
    @DisplayName("the docker profile with every broker variable set starts normally")
    void acceptsDockerProfileWithAllVariables() {
        environment.setActiveProfiles("docker");
        setAllBut("none");

        Assertions.assertDoesNotThrow(this::process);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("the local and the test profile have their own broker settings")
    void leavesOtherProfilesAlone(String profile) {
        environment.setActiveProfiles(profile);

        Assertions.assertDoesNotThrow(this::process);
    }

    @Test
    @DisplayName("the processor is registered with Spring Boot")
    void isRegisteredAsEnvironmentPostProcessor() {
        List<EnvironmentPostProcessor> processors = SpringFactoriesLoader
                .forDefaultResourceLocation(getClass().getClassLoader())
                .load(EnvironmentPostProcessor.class, (type, implementation, failure) -> {
                });

        Assertions.assertTrue(processors.stream().anyMatch(RequiredBrokerVariablesProcessor.class::isInstance),
                processors.toString());
    }
}
