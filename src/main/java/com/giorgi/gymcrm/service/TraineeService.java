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
    public void changePassword(String username, String password, String newPassword) {
        authenticationService.authenticate(username, password);
        requireText(newPassword, "New password");

        Trainee trainee = findOrThrow(username);
        trainee.getUser().setPassword(newPassword);
        traineeDao.update(trainee);
        log.info("Password changed for trainee: {}", username);
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
    public void activate(String username, String password) {
        authenticationService.authenticate(username, password);
        setActive(username, true);
    }

    @Transactional
    public void deactivate(String username, String password) {
        authenticationService.authenticate(username, password);
        setActive(username, false);
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

        Set<Trainer> trainers = new HashSet<>();
        for (String trainerUsername : trainerUsernames) {
            trainers.add(trainerDao.findByUsername(trainerUsername)
                    .orElseThrow(() -> new ProfileNotFoundException("Trainer not found: " + trainerUsername)));
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

    private void setActive(String username, boolean active) {
        Trainee trainee = findOrThrow(username);
        if (trainee.getUser().isActive() == active) {
            throw new IllegalArgumentException(
                    "Trainee " + username + " is already " + (active ? "active" : "inactive"));
        }
        trainee.getUser().setActive(active);
        traineeDao.update(trainee);
        log.info("Trainee {} set to {}", username, active ? "active" : "inactive");
    }

    private Trainee findOrThrow(String username) {
        return traineeDao.findByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("Trainee not found: " + username));
    }
}