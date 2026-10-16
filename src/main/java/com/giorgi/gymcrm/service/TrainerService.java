package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.dao.TrainingTypeDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import com.giorgi.gymcrm.util.CredentialGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

import static com.giorgi.gymcrm.util.Validations.requireText;

@Slf4j
@Service
public class TrainerService {

    private TrainerDao trainerDao;
    private TrainingTypeDao trainingTypeDao;
    private AuthenticationService authenticationService;
    private UsernameResolver usernameResolver;
    private CredentialGenerator credentialGenerator;

    @Autowired
    public void setTrainerDao(TrainerDao trainerDao) {
        this.trainerDao = trainerDao;
    }

    @Autowired
    public void setTrainingTypeDao(TrainingTypeDao trainingTypeDao) {
        this.trainingTypeDao = trainingTypeDao;
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Autowired
    public void setUsernameResolver(UsernameResolver usernameResolver) {
        this.usernameResolver = usernameResolver;
    }

    @Autowired
    public void setCredentialGenerator(CredentialGenerator credentialGenerator) {
        this.credentialGenerator = credentialGenerator;
    }

    @Transactional
    public Trainer createProfile(String firstName, String lastName, String specializationName) {
        requireText(firstName, "First name");
        requireText(lastName, "Last name");
        requireText(specializationName, "Specialization");

        TrainingType specialization = findTypeOrThrow(specializationName);

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(usernameResolver.generateUsername(firstName, lastName))
                .password(credentialGenerator.generatePassword())
                .isActive(true)
                .build();

        Trainer trainer = Trainer.builder()
                .user(user)
                .specialization(specialization)
                .build();

        trainerDao.save(trainer);
        log.info("Created trainer profile with username: {}", user.getUsername());
        return trainer;
    }

    @Transactional(readOnly = true)
    public Trainer selectByUsername(String username, String password) {
        authenticationService.authenticate(username, password);
        return findProfileOrThrow(username);
    }

    @Transactional
    public Trainer updateProfile(String username, String password,
                                 String firstName, String lastName, boolean isActive) {
        authenticationService.authenticate(username, password);
        requireText(firstName, "First name");
        requireText(lastName, "Last name");

        Trainer trainer = findProfileOrThrow(username);
        User user = trainer.getUser();
        user.setFirstName(firstName);
        user.setLastName(lastName);

        if (user.isActive() != isActive) {
            user.setActive(isActive);
            log.info("Trainer {} is {}", username, isActive ? "activated" : "deactivated");
        }

        Trainer updated = trainerDao.update(trainer);
        log.info("Updated trainer profile: {}", username);
        return updated;
    }

    private Trainer findProfileOrThrow(String username) {
        return trainerDao.findProfileByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("Trainer not found: " + username));
    }

    private TrainingType findTypeOrThrow(String name) {
        return trainingTypeDao.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown training type: " + name + ". Valid values: " + validTypeNames()));
    }

    private String validTypeNames() {
        return trainingTypeDao.findAll().stream()
                .map(TrainingType::getName)
                .collect(Collectors.joining(", "));
    }
}