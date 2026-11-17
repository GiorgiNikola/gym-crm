package com.giorgi.workload.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.List;
import java.util.Set;

abstract class RequiredDeployedVariableProcessor implements EnvironmentPostProcessor {

    private static final Set<String> DEPLOYED_PROFILES = Set.of("docker");

    protected abstract List<String> variables();

    protected abstract String message(String profile, String variable);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (String profile : environment.getActiveProfiles()) {
            if (DEPLOYED_PROFILES.contains(profile)) {
                requireVariables(environment, profile);
            }
        }
    }

    private void requireVariables(ConfigurableEnvironment environment, String profile) {
        for (String variable : variables()) {
            if (!environment.containsProperty(variable)) {
                throw new IllegalStateException(message(profile, variable));
            }
        }
    }
}
