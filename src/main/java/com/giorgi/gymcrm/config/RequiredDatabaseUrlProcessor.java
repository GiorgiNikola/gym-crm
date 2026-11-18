package com.giorgi.gymcrm.config;

import java.util.List;

public class RequiredDatabaseUrlProcessor extends RequiredDeployedVariableProcessor {

    private static final String DB_URL = "DB_URL";

    @Override
    protected List<String> variables() {
        return List.of(DB_URL);
    }

    @Override
    protected String message(String profile, String variable) {
        return "The " + profile + " profile has no default database url: set the " + DB_URL
                + " environment variable to the jdbc url of the " + profile + " database";
    }
}
