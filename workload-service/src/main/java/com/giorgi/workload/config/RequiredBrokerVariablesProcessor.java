package com.giorgi.workload.config;

import java.util.List;

public class RequiredBrokerVariablesProcessor extends RequiredDeployedVariableProcessor {

    @Override
    protected List<String> variables() {
        return List.of("ARTEMIS_URL", "ARTEMIS_USER", "ARTEMIS_PASSWORD");
    }

    @Override
    protected String message(String profile, String variable) {
        return "The " + profile + " profile has no default broker setting: set the " + variable
                + " environment variable";
    }
}
