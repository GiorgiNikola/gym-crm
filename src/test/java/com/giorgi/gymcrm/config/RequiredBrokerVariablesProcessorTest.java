package com.giorgi.gymcrm.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class RequiredBrokerVariablesProcessorTest {

    private final RequiredBrokerVariablesProcessor processor = new RequiredBrokerVariablesProcessor();
    private final MockEnvironment environment = new MockEnvironment();

    private void process() {
        processor.postProcessEnvironment(environment, null);
    }

    private void setAllBut(String missing) {
        for (String variable : new String[]{"ARTEMIS_URL", "ARTEMIS_USER", "ARTEMIS_PASSWORD"}) {
            if (!variable.equals(missing)) {
                environment.setProperty(variable, "set-by-the-deployment");
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"stg,ARTEMIS_URL", "prod,ARTEMIS_URL", "stg,ARTEMIS_USER", "prod,ARTEMIS_PASSWORD"})
    @DisplayName("a deployed profile missing a broker variable stops the application and names it")
    void stopsDeployedProfileWithoutVariable(String profile, String missing) {
        environment.setActiveProfiles(profile);
        setAllBut(missing);

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, this::process);

        Assertions.assertEquals("The " + profile + " profile has no default broker setting: set the " + missing
                + " environment variable", thrown.getMessage());
    }

    @Test
    @DisplayName("a deployed profile with every broker variable set starts normally")
    void acceptsDeployedProfileWithVariables() {
        environment.setActiveProfiles("prod");
        setAllBut("none");

        Assertions.assertDoesNotThrow(this::process);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "standalone"})
    @DisplayName("the development profiles fall back on a local broker, so the variables are optional")
    void leavesDevelopmentProfilesAlone(String profile) {
        environment.setActiveProfiles(profile);

        Assertions.assertDoesNotThrow(this::process);
    }
}
