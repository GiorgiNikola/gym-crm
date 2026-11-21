package com.giorgi.workload.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

// Runs before the JMS listener containers start, so no message is applied without the unique username index.
@Slf4j
@Component
public class MongoIndexInitializer implements SmartInitializingSingleton {

    public static final String USERNAME_INDEX = "username_unique";
    public static final String NAME_INDEX = "firstName_lastName";

    private final MongoTemplate mongoTemplate;

    public MongoIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void afterSingletonsInstantiated() {
        IndexOperations workloads = mongoTemplate.indexOps(TrainerWorkloadDocument.class);
        workloads.createIndex(new Index("username", Sort.Direction.ASC).unique().named(USERNAME_INDEX));
        workloads.createIndex(new Index("firstName", Sort.Direction.ASC)
                .on("lastName", Sort.Direction.ASC)
                .named(NAME_INDEX));
        log.info("MongoDB indexes ready on {}: {}, {}", TrainerWorkloadDocument.COLLECTION, USERNAME_INDEX, NAME_INDEX);
    }
}
