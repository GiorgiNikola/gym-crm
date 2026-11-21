package com.giorgi.workload.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.testcontainers.mongodb.MongoDBContainer;

@Profile("test")
@Configuration(proxyBeanMethods = false)
public class MongoTestContainerConfig {

    // Same 8.0 LTS tag as the mongo service in compose.yml.
    private static final String MONGO_IMAGE = "mongo:8.0.38";

    private static final MongoDBContainer MONGO = new MongoDBContainer(MONGO_IMAGE);

    // One container for every cached context; Testcontainers removes it when the JVM exits.
    @Bean(destroyMethod = "")
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return MONGO;
    }
}
