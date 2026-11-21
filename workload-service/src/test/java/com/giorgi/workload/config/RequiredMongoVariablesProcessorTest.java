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

class RequiredMongoVariablesProcessorTest {

    private final RequiredMongoVariablesProcessor processor = new RequiredMongoVariablesProcessor();
    private final MockEnvironment environment = new MockEnvironment();

    private void process() {
        processor.postProcessEnvironment(environment, null);
    }

    @Test
    @DisplayName("the docker profile without MONGO_URI stops and names the variable")
    void stopsDockerProfileWithoutUri() {
        environment.setActiveProfiles("docker");

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, this::process);

        Assertions.assertEquals("The docker profile has no default MongoDB connection: set the MONGO_URI "
                + "environment variable", thrown.getMessage());
    }

    @Test
    @DisplayName("the docker profile with MONGO_URI set starts normally")
    void acceptsDockerProfileWithUri() {
        environment.setActiveProfiles("docker");
        environment.setProperty("MONGO_URI", "from-the-environment");

        Assertions.assertDoesNotThrow(this::process);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test"})
    @DisplayName("the local and the test profile bring their own connection")
    void leavesOtherProfilesAlone(String profile) {
        environment.setActiveProfiles(profile);

        Assertions.assertDoesNotThrow(this::process);
    }

    @Test
    @DisplayName("the processor is registered with Spring Boot after the broker check")
    void isRegisteredAfterBrokerCheck() {
        List<EnvironmentPostProcessor> processors = SpringFactoriesLoader
                .forDefaultResourceLocation(getClass().getClassLoader())
                .load(EnvironmentPostProcessor.class, (type, implementation, failure) -> {
                });
        List<Class<?>> types = processors.stream().<Class<?>>map(Object::getClass).toList();

        Assertions.assertTrue(types.indexOf(RequiredMongoVariablesProcessor.class)
                > types.indexOf(RequiredBrokerVariablesProcessor.class), types.toString());
        Assertions.assertTrue(types.indexOf(RequiredBrokerVariablesProcessor.class) >= 0, types.toString());
    }
}
