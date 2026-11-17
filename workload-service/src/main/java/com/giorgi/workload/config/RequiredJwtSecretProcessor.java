package com.giorgi.workload.config;

import java.util.List;

public class RequiredJwtSecretProcessor extends RequiredDeployedVariableProcessor {

    private static final String JWT_SECRET = "WORKLOAD_JWT_SECRET";

    @Override
    protected List<String> variables() {
        return List.of(JWT_SECRET);
    }

    @Override
    protected String message(String profile, String variable) {
        return "The " + profile + " profile has no default token secret: "
                + "set the " + variable + " environment variable to a random value of at least 32 bytes";
    }
}
