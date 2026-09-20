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
        if (firstname == null) {
            log.error("First name is null");
            throw new IllegalArgumentException("First name must not be null");
        }
        if (firstname.isBlank()) {
            log.error("First name is blank");
            throw new IllegalArgumentException("First name must not be blank");
        }
        if (lastname == null) {
            log.error("Last name is null");
            throw new IllegalArgumentException("Last name must not be null");
        }
        if (lastname.isBlank()) {
            log.error("Last name is blank");
            throw new IllegalArgumentException("Last name must not be blank");
        }

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

        log.info("Creating trainee profile, username: {}", username);
        return traineeDao.save(trainee);
    }

    public Trainee updateTraineeProfile(Trainee trainee) {
        if (trainee == null) {
            log.error("Trainee is null");
            throw new IllegalArgumentException("Trainee must not be null");
        }
        return traineeDao.update(trainee);
    }

    public void deleteTraineeProfile(long id) {
        traineeDao.delete(id);
    }

    public Trainee selectTraineeProfile(long id) {
        return traineeDao.findById(id);
    }
}
