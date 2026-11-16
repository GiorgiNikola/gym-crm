package com.giorgi.workload.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class ProfileConfigTest {

    private static final String SECRET = "workload.security.jwt-secret";
    private static final String LOCAL_PREFIX = "${WORKLOAD_JWT_SECRET:";

    private String property(String resource, String key) throws IOException {
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load(resource, new ClassPathResource(resource));

        Assertions.assertEquals(1, loaded.size(), "expected a single yaml document in " + resource);
        return String.valueOf(loaded.getFirst().getProperty(key));
    }

    @Test
    @DisplayName("the base configuration carries no secret and falls back to the local profile")
    void baseConfigurationHasNoSecret() throws IOException {
        Assertions.assertEquals("null", property("application.yml", SECRET));
        Assertions.assertEquals("local", property("application.yml", "spring.profiles.default"));
    }

    @Test
    @DisplayName("the local profile has a marked development secret of at least 32 bytes")
    void localProfileHasMarkedDefault() throws IOException {
        String value = property("application-local.yml", SECRET);

        Assertions.assertTrue(value.startsWith(LOCAL_PREFIX), value);
        String fallback = value.substring(LOCAL_PREFIX.length(), value.length() - 1);
        Assertions.assertTrue(fallback.startsWith("local-development-only"), fallback);
        Assertions.assertTrue(fallback.getBytes(StandardCharsets.UTF_8).length >= 32, fallback);
    }

    @Test
    @DisplayName("the docker profile has no secret to fall back on")
    void dockerProfileRequiresTheVariable() throws IOException {
        Assertions.assertEquals("${WORKLOAD_JWT_SECRET}", property("application-docker.yml", SECRET));
    }

    @Test
    @DisplayName("the docker profile needs an eureka url and hides health details")
    void dockerProfileHidesDetails() throws IOException {
        Assertions.assertEquals("${EUREKA_URL}",
                property("application-docker.yml", "eureka.client.service-url.defaultZone"));
        Assertions.assertEquals("never", property("application-docker.yml", "management.endpoint.health.show-details"));
    }

    @Test
    @DisplayName("instances register under a configurable hostname, localhost by default")
    void registersConfigurableHostname() throws IOException {
        Assertions.assertEquals("${EUREKA_INSTANCE_HOSTNAME:localhost}",
                property("application.yml", "eureka.instance.hostname"));
    }

    @Test
    @DisplayName("the test profile keeps the eureka client off")
    void testProfileDisablesEureka() throws IOException {
        Assertions.assertEquals("false", property("application-test.yml", "eureka.client.enabled"));
    }

    @Test
    @DisplayName("the eureka client renews every 5 seconds, expires after 15 and reports its health")
    void configuresShortLease() throws IOException {
        Assertions.assertEquals("${EUREKA_URL:http://localhost:8761/eureka}",
                property("application.yml", "eureka.client.service-url.defaultZone"));
        Assertions.assertEquals("5", property("application.yml", "eureka.instance.lease-renewal-interval-in-seconds"));
        Assertions.assertEquals("15",
                property("application.yml", "eureka.instance.lease-expiration-duration-in-seconds"));
        Assertions.assertEquals("true", property("application.yml", "eureka.client.healthcheck.enabled"));
    }

    @Test
    @DisplayName("the instance status and the registry copy are refreshed every 5 seconds")
    void refreshesStatusQuickly() throws IOException {
        Assertions.assertEquals("5",
                property("application.yml", "eureka.client.instance-info-replication-interval-seconds"));
        Assertions.assertEquals("5", property("application.yml", "eureka.client.registry-fetch-interval-seconds"));
    }
}
