package com.giorgi.gymcrm.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class RequiredDatabaseUrlProcessorTest {

    private final RequiredDatabaseUrlProcessor processor = new RequiredDatabaseUrlProcessor();
    private final MockEnvironment environment = new MockEnvironment();

    private void process() {
        processor.postProcessEnvironment(environment, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"stg", "prod"})
    @DisplayName("a deployed profile without DB_URL stops the application and says so")
    void stopsDeployedProfileWithoutUrl(String profile) {
        environment.setActiveProfiles(profile);

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class, this::process);

        Assertions.assertEquals("The " + profile + " profile has no default database url: set the DB_URL "
                + "environment variable to the jdbc url of the " + profile + " database", thrown.getMessage());
    }

    @Test
    @DisplayName("a deployed profile with DB_URL set starts normally")
    void acceptsDeployedProfileWithUrl() {
        environment.setActiveProfiles("prod");
        environment.setProperty("DB_URL", "jdbc:postgresql://db.internal:5432/gymcrm");

        Assertions.assertDoesNotThrow(this::process);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev"})
    @DisplayName("the development profiles bring their own url, so DB_URL is optional")
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
