package com.giorgi.gymcrm.config;

public class RequiredDatabaseUrlProcessor extends RequiredDeployedVariableProcessor {

    private static final String DB_URL = "DB_URL";

    @Override
    protected String variable() {
        return DB_URL;
    }

    @Override
    protected String message(String profile) {
        return "The " + profile + " profile has no default database url: set the " + DB_URL
                + " environment variable to the jdbc url of the " + profile + " database";
    }
}
