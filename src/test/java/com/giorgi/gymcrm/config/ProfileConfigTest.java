package com.giorgi.gymcrm.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

class ProfileConfigTest {

    private static final String URL = "spring.datasource.url";
    private static final String POOL = "spring.datasource.hikari.maximum-pool-size";
    private static final String DDL_AUTO = "spring.jpa.hibernate.ddl-auto";

    private PropertySource<?> profile(String name) throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load(name, new ClassPathResource("application-" + name + ".yml"));

        Assertions.assertEquals(1, loaded.size(), "expected a single yaml document in " + name);
        return loaded.get(0);
    }

    private String property(String profile, String key) throws IOException {
        return String.valueOf(profile(profile).getProperty(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "stg", "prod"})
    @DisplayName("every profile brings its own database url")
    void everyProfileHasItsOwnUrl(String profile) throws IOException {
        Assertions.assertNotNull(profile(profile).getProperty(URL));
    }

    @Test
    @DisplayName("local and dev point at different databases")
    void localAndDevDoNotShareADatabase() throws IOException {
        Assertions.assertNotEquals(property("local", URL), property("dev", URL));
    }

    @Test
    @DisplayName("local points at a database on this machine")
    void localUsesLocalhost() throws IOException {
        Assertions.assertEquals("jdbc:postgresql://localhost:5432/gymcrm", property("local", URL));
    }

    @Test
    @DisplayName("dev falls back to its own database when DB_URL is not set")
    void devHasAFallbackUrl() throws IOException {
        Assertions.assertEquals("${DB_URL:jdbc:postgresql://localhost:5432/gymcrm_dev}",
                property("dev", URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stg", "prod"})
    @DisplayName("staging and production have no url to fall back on")
    void deployedProfilesRequireTheUrl(String profile) throws IOException {
        Assertions.assertEquals("${DB_URL}", property(profile, URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev", "stg", "prod"})
    @DisplayName("no profile lets Hibernate touch the schema")
    void everyProfileOnlyValidates(String profile) throws IOException {
        Assertions.assertEquals("validate", property(profile, DDL_AUTO));
    }

    @ParameterizedTest
    @CsvSource({"local,5", "dev,5", "stg,10", "prod,20"})
    @DisplayName("the pool grows with the environment")
    void poolSizesMatchTheEnvironment(String profile, String size) throws IOException {
        Assertions.assertEquals(size, property(profile, POOL));
    }

    @ParameterizedTest
    @CsvSource({"local,true", "dev,true", "stg,false", "prod,false"})
    @DisplayName("sql only shows up in the two development profiles")
    void sqlLoggingIsOffWhenDeployed(String profile, String showSql) throws IOException {
        Assertions.assertEquals(showSql, property(profile, "spring.jpa.show-sql"));
    }

    @Test
    @DisplayName("only production quiets the root logger")
    void productionQuietsEverythingElse() throws IOException {
        Assertions.assertEquals("WARN", property("prod", "logging.level.root"));
        Assertions.assertNull(profile("stg").getProperty("logging.level.root"));
    }

    @ParameterizedTest
    @CsvSource({"local,DEBUG", "dev,DEBUG", "stg,INFO", "prod,INFO"})
    @DisplayName("the app log level drops once the profile is deployed")
    void appLogLevelMatchesTheEnvironment(String profile, String level) throws IOException {
        Assertions.assertEquals(level, property(profile, "logging.level.com.giorgi.gymcrm"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stg", "prod"})
    @DisplayName("a deployed profile serves neither the spec nor the docs UI")
    void deployedProfilesTurnTheDocsOff(String profile) throws IOException {
        Assertions.assertEquals("false", property(profile, "springdoc.api-docs.enabled"));
        Assertions.assertEquals("false", property(profile, "springdoc.swagger-ui.enabled"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "dev"})
    @DisplayName("the development profiles leave the docs at the springdoc default")
    void developmentProfilesSayNothingAboutTheDocs(String profile) throws IOException {
        Assertions.assertNull(profile(profile).getProperty("springdoc.api-docs.enabled"));
        Assertions.assertNull(profile(profile).getProperty("springdoc.swagger-ui.enabled"));
    }
}
