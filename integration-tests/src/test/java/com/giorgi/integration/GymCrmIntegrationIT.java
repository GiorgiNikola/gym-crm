package com.giorgi.integration;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PUBLISH_QUIET_PROPERTY_NAME;

@Suite
@SuiteDisplayName("gym-crm system integration tests")
@IncludeEngines("cucumber")
@IncludeTags("integration")
@SelectClasspathResource("features/integration")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.giorgi.integration")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,"
        + "html:target/cucumber-reports/integration.html,"
        + "json:target/cucumber-reports/integration.json")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
class GymCrmIntegrationIT {
}
