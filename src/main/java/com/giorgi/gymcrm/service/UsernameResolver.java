package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.util.CredentialGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UsernameResolver {
    private TraineeDao traineeDao;
    private TrainerDao trainerDao;
    private CredentialGenerator credentialGenerator;

    @Autowired
    public void setTraineeDao(TraineeDao traineeDao) {
        this.traineeDao = traineeDao;
    }

    @Autowired
    public void setTrainerDao(TrainerDao trainerDao) {
        this.trainerDao = trainerDao;
    }

    @Autowired
    public void setCredentialGenerator(CredentialGenerator credentialGenerator) {
        this.credentialGenerator = credentialGenerator;
    }

    public String generateUsername(String firstName, String lastName) {
        String baseUsername = credentialGenerator.generateUsername(firstName, lastName);
        String candidate = baseUsername;
        int serial = 1;
        while (traineeDao.existsByUsername(candidate) || trainerDao.existsByUsername(candidate)) {
            log.debug("Username {} already taken, trying next candidate", candidate);
            candidate = credentialGenerator.addSerialNumberToUsername(baseUsername, serial++);
        }
        log.info("Resolved username: {}", candidate);
        return candidate;
    }
}
