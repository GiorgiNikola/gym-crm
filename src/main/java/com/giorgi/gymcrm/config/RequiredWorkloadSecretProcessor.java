package com.giorgi.gymcrm.config;

import java.util.List;

public class RequiredWorkloadSecretProcessor extends RequiredDeployedVariableProcessor {

    private static final String WORKLOAD_JWT_SECRET = "WORKLOAD_JWT_SECRET";

    @Override
    protected List<String> variables() {
        return List.of(WORKLOAD_JWT_SECRET);
    }

    @Override
    protected String message(String profile, String variable) {
        return "The " + profile + " profile has no default workload service token secret: set the "
                + WORKLOAD_JWT_SECRET + " environment variable to the secret workload-service validates with, "
                + "a random value of at least 32 bytes";
    }
}
