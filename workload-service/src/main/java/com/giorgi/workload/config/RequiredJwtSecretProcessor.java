package com.giorgi.workload.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;

public class RequiredJwtSecretProcessor implements EnvironmentPostProcessor {

    private static final String DOCKER_PROFILE = "docker";
    private static final String JWT_SECRET = "WORKLOAD_JWT_SECRET";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean docker = Arrays.asList(environment.getActiveProfiles()).contains(DOCKER_PROFILE);

        if (docker && !environment.containsProperty(JWT_SECRET)) {
            throw new IllegalStateException("The " + DOCKER_PROFILE + " profile has no default token secret: "
                    + "set the " + JWT_SECRET + " environment variable to a random value of at least 32 bytes");
        }
    }
}
