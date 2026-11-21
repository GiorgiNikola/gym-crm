package com.giorgi.workload.persistence;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TrainerWorkloadMongoRepository extends MongoRepository<TrainerWorkloadDocument, ObjectId> {

    Optional<TrainerWorkloadDocument> findByUsername(String username);
}
