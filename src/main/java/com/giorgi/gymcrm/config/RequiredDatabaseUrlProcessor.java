package com.giorgi.gymcrm.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Set;

// stg and prod have no fallback url, and the property binder leaves an unresolved ${DB_URL}
// in place, so Hikari would only complain that the url does not start with "jdbc". This says
// what is actually missing, before any bean is built.
public class RequiredDatabaseUrlProcessor implements EnvironmentPostProcessor {

    private static final Set<String> PROFILES_WITHOUT_A_DEFAULT_URL = Set.of("stg", "prod");
    private static final String DB_URL = "DB_URL";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String profile : environment.getActiveProfiles()) {
            if (PROFILES_WITHOUT_A_DEFAULT_URL.contains(profile) && !environment.containsProperty(DB_URL)) {
                throw new IllegalStateException("The " + profile + " profile has no default database url: set the "
                        + DB_URL + " environment variable to the jdbc url of the " + profile + " database");
            }
        }
    }
}
