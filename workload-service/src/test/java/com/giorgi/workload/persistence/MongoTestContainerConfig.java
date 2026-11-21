package com.giorgi.workload.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.mongodb.MongoDBContainer;

@Profile("test")
@Configuration(proxyBeanMethods = false)
public class MongoTestContainerConfig {

    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    // One container for every cached context; Testcontainers removes it when the JVM exits.
    @Bean(destroyMethod = "")
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return MONGO;
    }
}
