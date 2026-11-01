package com.giorgi.gymcrm.config;

public class RequiredJwtSecretProcessor extends RequiredDeployedVariableProcessor {

    private static final String JWT_SECRET = "JWT_SECRET";

    @Override
    protected String variable() {
        return JWT_SECRET;
    }

    @Override
    protected String message(String profile) {
        return "The " + profile + " profile has no default token secret: set the " + JWT_SECRET
                + " environment variable to a random value of at least 32 bytes";
    }
}
