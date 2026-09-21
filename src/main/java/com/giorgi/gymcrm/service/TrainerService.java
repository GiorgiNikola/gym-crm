package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.util.CredentialGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrainerService {
    private TrainerDao trainerDao;
    private UsernameResolver usernameResolver;
    private CredentialGenerator credentialGenerator;

    @Autowired
    public void setTrainerDao(TrainerDao trainerDao) {
        this.trainerDao = trainerDao;
    }

    @Autowired
    public void setUsernameResolver(UsernameResolver usernameResolver) {
        this.usernameResolver = usernameResolver;
    }

    @Autowired
    public void setCredentialGenerator(CredentialGenerator credentialGenerator) {
        this.credentialGenerator = credentialGenerator;
    }

    public Trainer createTrainerProfile(String firstname,
                                        String lastname,
                                        boolean isActive,
                                        TrainingType specialization) {
        validateNames(firstname, lastname);
        if (specialization == null) {
            log.warn("Trainer validation failed, specialization is missing");
            throw new IllegalArgumentException("Specialization must not be null");
        }

        long id = trainerDao.generateId();
        String username = usernameResolver.generateUsername(firstname, lastname);
        String password = credentialGenerator.generatePassword();

        Trainer trainer = Trainer.builder()
                .userID(id)
                .firstName(firstname)
                .lastName(lastname)
                .username(username)
                .password(password)
                .isActive(isActive)
                .specialization(specialization)
                .build();

        Trainer savedTrainer = trainerDao.save(trainer);
        log.info("Created trainer profile, id: {}, username: {}", savedTrainer.getUserID(), savedTrainer.getUsername());
        return savedTrainer;
    }

    public Trainer updateTrainerProfile(Trainer trainer) {
        if (trainer == null) {
            log.warn("Trainer update rejected, trainer is null");
            throw new IllegalArgumentException("Trainer must not be null");
        }

        Trainer existingTrainer = trainerDao.findById(trainer.getUserID());
        if (existingTrainer == null) {
            log.warn("Trainer update rejected, trainer with id: {} does not exist", trainer.getUserID());
            throw new IllegalArgumentException("Trainer with id: " + trainer.getUserID() + " does not exist");
        }

        validateNames(trainer.getFirstName(), trainer.getLastName());

        Trainer updatedTrainer = trainer.toBuilder()
                .username(existingTrainer.getUsername())
                .password(existingTrainer.getPassword())
                .specialization(existingTrainer.getSpecialization())
                .build();

        Trainer savedTrainer = trainerDao.update(updatedTrainer);
        log.info("Updated trainer profile, id: {}, username: {}", savedTrainer.getUserID(), savedTrainer.getUsername());
        return savedTrainer;
    }

    public Trainer selectTrainerProfile(long id) {
        log.debug("Selecting trainer profile, id: {}", id);
        return trainerDao.findById(id);
    }

    private void validateNames(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank()) {
            log.warn("Trainer validation failed, first name is missing");
            throw new IllegalArgumentException("First name must not be null or blank");
        }
        if (lastName == null || lastName.isBlank()) {
            log.warn("Trainer validation failed, last name is missing");
            throw new IllegalArgumentException("Last name must not be null or blank");
        }
    }
}