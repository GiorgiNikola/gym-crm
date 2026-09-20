package com.giorgi.gymcrm.storage;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StorageInitializer implements BeanPostProcessor {
    @Value("${trainee.file.path}")
    private String traineeFilePath;

    @Value("${trainer.file.path}")
    private String trainerFilePath;

    @Value("${training.file.path}")
    private String trainingFilePath;

    private ResourceLoader resourceLoader;

    @Autowired
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if ("traineeStorage".equals(beanName)) {
            loadTrainees((Map<Long, Trainee>) bean, traineeFilePath);
        }
        if ("trainerStorage".equals(beanName)) {
            loadTrainers((Map<Long, Trainer>) bean, trainerFilePath);
        }
        if ("trainingStorage".equals(beanName)) {
            loadTrainings((Map<Long, Training>) bean, trainingFilePath);
        }
        return bean;
    }

    private void loadTrainees(Map<Long, Trainee> traineeStorage, String traineeFilePath) {
        List<String> traineeEntries = readLines(traineeFilePath);
        for (int i = 1; i < traineeEntries.size(); i++) {
            String[] traineeDetails = traineeEntries.get(i).split(",", -1);

            Trainee trainee = Trainee.builder()
                    .userID(Long.parseLong(traineeDetails[0]))
                    .firstName(traineeDetails[1])
                    .lastName(traineeDetails[2])
                    .username(traineeDetails[3])
                    .password(traineeDetails[4])
                    .isActive(Boolean.parseBoolean(traineeDetails[5]))
                    .dateOfBirth(traineeDetails[6].isBlank() ? null : LocalDate.parse(traineeDetails[6]))
                    .address(traineeDetails[7])
                    .build();

            traineeStorage.put(trainee.getUserID(), trainee);
        }

        int rows = traineeEntries.size() - 1;
        if (traineeStorage.size() < rows) {
            log.warn("Loaded {} trainees from {} but the file had {} rows, ids are duplicated",
                    traineeStorage.size(), traineeFilePath, rows);
        }
        log.info("Loaded {} trainees from {}", traineeStorage.size(), traineeFilePath);
    }

    private void loadTrainers(Map<Long, Trainer> trainerStorage, String trainerFilePath) {
        List<String> trainerEntries = readLines(trainerFilePath);
        for (int i = 1; i < trainerEntries.size(); i++) {
            String[] trainerDetails = trainerEntries.get(i).split(",", -1);

            Trainer trainer = Trainer.builder()
                    .userID(Long.parseLong(trainerDetails[0]))
                    .firstName(trainerDetails[1])
                    .lastName(trainerDetails[2])
                    .username(trainerDetails[3])
                    .password(trainerDetails[4])
                    .isActive(Boolean.parseBoolean(trainerDetails[5]))
                    .specialization(TrainingType.valueOf(trainerDetails[6]))
                    .build();

            trainerStorage.put(trainer.getUserID(), trainer);
        }

        int rows = trainerEntries.size() - 1;
        if (trainerStorage.size() < rows) {
            log.warn("Loaded {} trainers from {} but the file had {} rows, ids are duplicated",
                    trainerStorage.size(), trainerFilePath, rows);
        }
        log.info("Loaded {} trainers from {}", trainerStorage.size(), trainerFilePath);
    }

    private void loadTrainings(Map<Long, Training> trainingStorage, String trainingFilePath) {
        List<String> trainingEntries = readLines(trainingFilePath);
        for (int i = 1; i < trainingEntries.size(); i++) {
            String[] trainingDetails = trainingEntries.get(i).split(",", -1);

            Training training = Training.builder()
                    .ID(Long.parseLong(trainingDetails[0]))
                    .traineeID(Long.parseLong(trainingDetails[1]))
                    .trainerID(Long.parseLong(trainingDetails[2]))
                    .name(trainingDetails[3])
                    .type(TrainingType.valueOf(trainingDetails[4]))
                    .date(trainingDetails[5].isBlank() ? null : LocalDate.parse(trainingDetails[5]))
                    .duration(Long.parseLong(trainingDetails[6]))
                    .build();

            trainingStorage.put(training.getID(), training);
        }

        int rows = trainingEntries.size() - 1;
        if (trainingStorage.size() < rows) {
            log.warn("Loaded {} trainings from {} but the file had {} rows, ids are duplicated",
                    trainingStorage.size(), trainingFilePath, rows);
        }
        log.info("Loaded {} trainings from {}", trainingStorage.size(), trainingFilePath);
    }

    private List<String> readLines(String path) {
        Resource resource = resourceLoader.getResource(path);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read storage file: " + path, e);
        }
    }
}
