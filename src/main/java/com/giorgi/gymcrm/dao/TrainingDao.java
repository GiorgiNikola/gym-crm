package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.Training;

import java.time.LocalDate;
import java.util.List;

public interface TrainingDao {
    Training save(Training training);
    List<Training> findTraineeTrainings(String traineeUsername,
                                        LocalDate fromDate,
                                        LocalDate toDate,
                                        String trainerName,
                                        String trainingTypeName);

    List<Training> findTrainerTrainings(String trainerUsername,
                                        LocalDate fromDate,
                                        LocalDate toDate,
                                        String traineeName);
}