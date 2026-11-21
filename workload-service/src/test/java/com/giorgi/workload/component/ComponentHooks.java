package com.giorgi.workload.component;

import com.giorgi.workload.persistence.TrainerWorkloadMongoRepository;
import io.cucumber.java.Before;

public class ComponentHooks {

    private final TrainerWorkloadMongoRepository documents;
    private final ComponentBroker broker;

    public ComponentHooks(TrainerWorkloadMongoRepository documents, ComponentBroker broker) {
        this.documents = documents;
        this.broker = broker;
    }

    @Before
    public void startClean() throws Exception {
        documents.deleteAll();
        broker.drain();
    }
}
