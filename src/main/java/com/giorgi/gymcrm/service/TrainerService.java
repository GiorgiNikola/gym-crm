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
        if (specialization == null) {
            log.error("Specialization is null");
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

        log.info("Creating trainer profile, username: {}", username);
        return trainerDao.save(trainer);
    }

    public Trainer updateTrainerProfile(Trainer trainer) {
        if (trainer == null) {
            log.error("Trainer is null");
            throw new IllegalArgumentException("Trainer must not be null");
        }
        return trainerDao.update(trainer);
    }

    public Trainer selectTrainerProfile(long id) {
        return trainerDao.findById(id);
    }
}
