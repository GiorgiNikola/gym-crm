package com.giorgi.gymcrm.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

class StartupInfoLoggerTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/gymcrm";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final MockEnvironment environment = new MockEnvironment();

    private Logger startupLogger;

    @BeforeEach
    void captureLogs() {
        environment.setProperty("spring.datasource.url", URL);
        startupLogger = (Logger) LoggerFactory.getLogger(StartupInfoLogger.class);
        appender.start();
        startupLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        startupLogger.detachAppender(appender);
        appender.stop();
    }

    private String logged() {
        Assertions.assertEquals(1, appender.list.size());
        return appender.list.get(0).getFormattedMessage();
    }

    @Test
    @DisplayName("the startup line names the active profile and the database")
    void logsActiveProfileAndUrl() {
        environment.setActiveProfiles("stg");

        new StartupInfoLogger(environment).logDatabaseTarget();

        Assertions.assertEquals("Started with profile(s) stg talking to " + URL, logged());
    }

    @Test
    @DisplayName("with no profile chosen the default one is reported")
    void logsDefaultProfileWhenNoneIsActive() {
        environment.setDefaultProfiles("local");

        new StartupInfoLogger(environment).logDatabaseTarget();

        Assertions.assertEquals("Started with profile(s) local talking to " + URL, logged());
    }

    @Test
    @DisplayName("the password never reaches the startup line")
    void keepsCredentialsOut() {
        environment.setProperty("spring.datasource.password", "aX7kQ2mN9p");
        environment.setActiveProfiles("prod");

        new StartupInfoLogger(environment).logDatabaseTarget();

        Assertions.assertFalse(logged().contains("aX7kQ2mN9p"), logged());
    }
}
