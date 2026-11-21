package com.giorgi.workload.config;

import java.util.List;

public class RequiredMongoVariablesProcessor extends RequiredDeployedVariableProcessor {

    @Override
    protected List<String> variables() {
        return List.of("MONGO_URI");
    }

    @Override
    protected String message(String profile, String variable) {
        return "The " + profile + " profile has no default MongoDB connection: set the " + variable
                + " environment variable";
    }
}
