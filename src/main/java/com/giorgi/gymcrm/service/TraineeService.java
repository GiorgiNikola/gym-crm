package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.util.CredentialGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Slf4j
@Service
public class TraineeService {
    private TraineeDao traineeDao;
    private UsernameResolver usernameResolver;
    private CredentialGenerator credentialGenerator;

    @Autowired
    public void setTraineeDao(TraineeDao traineeDao) {
        this.traineeDao = traineeDao;
    }

    @Autowired
    public void setUsernameResolver(UsernameResolver usernameResolver) {
        this.usernameResolver = usernameResolver;
    }

    @Autowired
    public void setCredentialGenerator(CredentialGenerator credentialGenerator) {
        this.credentialGenerator = credentialGenerator;
    }

    public Trainee createTraineeProfile(String firstname,
                                        String lastname,
                                        boolean isActive,
                                        LocalDate dateOfBirth,
                                        String address) {
        validateNames(firstname, lastname);

        long id = traineeDao.generateId();
        String username = usernameResolver.generateUsername(firstname, lastname);
        String password = credentialGenerator.generatePassword();

        Trainee trainee = Trainee.builder()
                .userID(id)
                .firstName(firstname)
                .lastName(lastname)
                .username(username)
                .password(password)
                .isActive(isActive)
                .dateOfBirth(dateOfBirth)
                .address(address)
                .build();

        Trainee savedTrainee = traineeDao.save(trainee);
        log.info("Created trainee profile, id: {}, username: {}", savedTrainee.getUserID(), savedTrainee.getUsername());
        return savedTrainee;
    }

    public Trainee updateTraineeProfile(Trainee trainee) {
        if (trainee == null) {
            log.warn("Trainee update rejected, trainee is null");
            throw new IllegalArgumentException("Trainee must not be null");
        }

        Trainee existingTrainee = traineeDao.findById(trainee.getUserID());
        if (existingTrainee == null) {
            log.warn("Trainee update rejected, trainee with id: {} does not exist", trainee.getUserID());
            throw new IllegalArgumentException("Trainee with id: " + trainee.getUserID() + " does not exist");
        }

        validateNames(trainee.getFirstName(), trainee.getLastName());

        Trainee updatedTrainee = trainee.toBuilder()
                .username(existingTrainee.getUsername())
                .password(existingTrainee.getPassword())
                .build();

        Trainee savedTrainee = traineeDao.update(updatedTrainee);
        log.info("Updated trainee profile, id: {}, username: {}", savedTrainee.getUserID(), savedTrainee.getUsername());
        return savedTrainee;
    }

    public void deleteTraineeProfile(long id) {
        if (!traineeDao.existsByID(id)) {
            log.warn("Trainee delete skipped, trainee with id: {} does not exist", id);
            return;
        }

        traineeDao.delete(id);
        log.info("Deleted trainee profile, id: {}", id);
    }

    public Trainee selectTraineeProfile(long id) {
        log.debug("Selecting trainee profile, id: {}", id);
        return traineeDao.findById(id);
    }

    private void validateNames(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank()) {
            log.warn("Trainee validation failed, first name is missing");
            throw new IllegalArgumentException("First name must not be null or blank");
        }
        if (lastName == null || lastName.isBlank()) {
            log.warn("Trainee validation failed, last name is missing");
            throw new IllegalArgumentException("Last name must not be null or blank");
        }
    }
}