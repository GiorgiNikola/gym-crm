package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.User;
import com.giorgi.gymcrm.util.CredentialGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    private TraineeDao traineeDao;
    private TrainerDao trainerDao;
    private AuthenticationService authenticationService;
    private UsernameResolver usernameResolver;
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
    public Trainee createProfile(String firstName, String lastName, LocalDate dateOfBirth, String address) {
        requireText(firstName, "First name");
        requireText(lastName, "Last name");

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(usernameResolver.generateUsername(firstName, lastName))
                .password(credentialGenerator.generatePassword())
                .isActive(true)
                .build();

        Trainee trainee = Trainee.builder()
                .user(user)
                .dateOfBirth(dateOfBirth)
                .address(address)
                .build();

        traineeDao.save(trainee);
        log.info("Created trainee profile with username: {}", user.getUsername());
        return trainee;
    }

    @Transactional(readOnly = true)
    public Trainee selectByUsername(String username, String password) {
        authenticationService.authenticate(username, password);
        return findOrThrow(username);
    }

    @Transactional
    public Trainee updateProfile(String username, String password,
                                 String firstName, String lastName,
                                 LocalDate dateOfBirth, String address) {
        authenticationService.authenticate(username, password);
        requireText(firstName, "First name");
        requireText(lastName, "Last name");

        Trainee trainee = findOrThrow(username);
        trainee.getUser().setFirstName(firstName);
        trainee.getUser().setLastName(lastName);
        trainee.setDateOfBirth(dateOfBirth);
        trainee.setAddress(address);

        Trainee updated = traineeDao.update(trainee);
        log.info("Updated trainee profile: {}", username);
        return updated;
    }

    @Transactional
    public void deleteByUsername(String username, String password) {
        authenticationService.authenticate(username, password);

        Trainee trainee = findOrThrow(username);
        traineeDao.delete(trainee);
        log.info("Deleted trainee profile: {}", username);
    }

    @Transactional
    public Trainee updateTrainers(String username, String password, Set<String> trainerUsernames) {
        authenticationService.authenticate(username, password);
        if (trainerUsernames == null) {
            throw new IllegalArgumentException("Trainer usernames must not be null");
        }

        Trainee trainee = findOrThrow(username);

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
    public List<Trainer> findUnassignedTrainers(String username, String password) {
        authenticationService.authenticate(username, password);
        findOrThrow(username);
        return trainerDao.findNotAssignedToTrainee(username);
    }

    private Trainee findOrThrow(String username) {
        return traineeDao.findByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("Trainee not found: " + username));
    }
}