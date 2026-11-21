package com.giorgi.workload.component;

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
@SuiteDisplayName("workload-service component tests")
@IncludeEngines("cucumber")
@IncludeTags("component")
@SelectClasspathResource("features/component")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.giorgi.workload.component")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,"
        + "html:target/cucumber-reports/workload-component.html,"
        + "json:target/cucumber-reports/workload-component.json")
@ConfigurationParameter(key = PLUGIN_PUBLISH_QUIET_PROPERTY_NAME, value = "true")
class WorkloadComponentTest {
}
