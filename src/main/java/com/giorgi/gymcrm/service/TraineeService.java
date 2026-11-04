package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.metrics.GymMetrics;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.giorgi.gymcrm.util.Validations.requireText;

@Slf4j
@Service
public class TraineeService {

    private final TraineeDao traineeDao;
    private final TrainerDao trainerDao;
    private final GymMetrics gymMetrics;
    private final UserFactory userFactory;

    public TraineeService(TraineeDao traineeDao,
                          TrainerDao trainerDao,
                          GymMetrics gymMetrics,
                          UserFactory userFactory) {
        this.traineeDao = traineeDao;
        this.trainerDao = trainerDao;
        this.gymMetrics = gymMetrics;
        this.userFactory = userFactory;
    }

    @Transactional
    public NewUser createProfile(String firstName, String lastName, LocalDate dateOfBirth, String address) {
        requireText(firstName, "First name");
        requireText(lastName, "Last name");

        NewUser created = userFactory.create(firstName, lastName);
        Trainee trainee = Trainee.builder()
                .user(created.user())
                .dateOfBirth(dateOfBirth)
                .address(address)
                .build();

        traineeDao.save(trainee);
        gymMetrics.traineeRegistered();
        log.info("Created trainee profile with username: {}", created.user().getUsername());
        return created;
    }

    @Transactional(readOnly = true)
    public Trainee selectByUsername(String username) {
        return findProfileOrThrow(username);
    }

    @Transactional
    public Trainee updateProfile(String username,
                                 String firstName, String lastName,
                                 LocalDate dateOfBirth, String address, boolean isActive) {
        requireText(firstName, "First name");
        requireText(lastName, "Last name");

        Trainee trainee = findProfileOrThrow(username);
        User user = trainee.getUser();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        trainee.setDateOfBirth(dateOfBirth);
        trainee.setAddress(address);

        if (user.isActive() != isActive) {
            user.setActive(isActive);
            log.info("Trainee {} is {}", username, isActive ? "activated" : "deactivated");
        }

        Trainee updated = traineeDao.update(trainee);
        log.info("Updated trainee profile: {}", username);
        return updated;
    }

    @Transactional
    public void deleteByUsername(String username) {
        Trainee trainee = findOrThrow(username);
        traineeDao.delete(trainee);
        log.info("Deleted trainee profile: {}", username);
    }

    @Transactional
    public Trainee updateTrainers(String username, Set<String> trainerUsernames) {
        if (trainerUsernames == null) {
            throw new IllegalArgumentException("Trainer usernames must not be null");
        }

        Trainee trainee = findProfileOrThrow(username);

        Set<Trainer> trainers = new HashSet<>(trainerDao.findAllByUsernames(trainerUsernames));
        if (trainers.size() != trainerUsernames.size()) {
            Set<String> found = trainers.stream()
                    .map(trainer -> trainer.getUser().getUsername())
                    .collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(trainerUsernames);
            missing.removeAll(found);
            throw new ProfileNotFoundException("Trainers not found: " + String.join(", ", missing));
        }

        trainee.setTrainers(trainers);
        Trainee updated = traineeDao.update(trainee);
        log.info("Updated trainer list for trainee {}, {} trainers assigned", username, trainers.size());
        return updated;
    }

    @Transactional(readOnly = true)
    public List<Trainer> findUnassignedTrainers(String username) {
        findOrThrow(username);
        return trainerDao.findNotAssignedToTrainee(username);
    }

    private Trainee findOrThrow(String username) {
        return traineeDao.findByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("Trainee not found: " + username));
    }

    private Trainee findProfileOrThrow(String username) {
        return traineeDao.findProfileByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("Trainee not found: " + username));
    }
}
