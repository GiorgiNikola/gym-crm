package com.giorgi.workload.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Workload service",
        version = "v1",
        description = "Keeps the monthly training time of every trainer, in minutes"))
public class OpenApiConfig {
}
