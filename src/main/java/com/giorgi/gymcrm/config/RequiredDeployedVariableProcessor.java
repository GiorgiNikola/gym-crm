package com.giorgi.gymcrm.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Set;

abstract class RequiredDeployedVariableProcessor implements EnvironmentPostProcessor {

    private static final Set<String> DEPLOYED_PROFILES = Set.of("stg", "prod");

    protected abstract String variable();

    protected abstract String message(String profile);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String profile : environment.getActiveProfiles()) {
            if (DEPLOYED_PROFILES.contains(profile) && !environment.containsProperty(variable())) {
                throw new IllegalStateException(message(profile));
            }
        }
    }
}
