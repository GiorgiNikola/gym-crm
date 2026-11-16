package com.giorgi.gymcrm.workload;

import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.User;

import java.time.LocalDate;

public record WorkloadRequest(
        String trainerUsername,
        String trainerFirstName,
        String trainerLastName,
        boolean isActive,
        LocalDate trainingDate,
        int trainingDuration,
        ActionType actionType) {

    public static WorkloadRequest of(Training training, ActionType actionType) {
        User trainer = training.getTrainer().getUser();
        return new WorkloadRequest(trainer.getUsername(), trainer.getFirstName(), trainer.getLastName(),
                trainer.isActive(), training.getDate(), Math.toIntExact(training.getDuration()), actionType);
    }
}
