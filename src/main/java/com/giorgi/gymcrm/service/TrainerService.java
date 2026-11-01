package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainerDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.metrics.GymMetrics;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.TrainingType;
import com.giorgi.gymcrm.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.giorgi.gymcrm.util.Validations.requireText;

@Slf4j
@Service
public class TrainerService {

    private final TrainerDao trainerDao;
    private final TrainingTypeService trainingTypeService;
    private final GymMetrics gymMetrics;
    private final UserFactory userFactory;

    public TrainerService(TrainerDao trainerDao,
                          TrainingTypeService trainingTypeService,
                          GymMetrics gymMetrics,
                          UserFactory userFactory) {
        this.trainerDao = trainerDao;
        this.trainingTypeService = trainingTypeService;
        this.gymMetrics = gymMetrics;
        this.userFactory = userFactory;
    }

    @Transactional
    public NewUser createProfile(String firstName, String lastName, String specializationName) {
        requireText(firstName, "First name");
        requireText(lastName, "Last name");
        requireText(specializationName, "Specialization");

        TrainingType specialization = trainingTypeService.requireByName(specializationName);

        NewUser created = userFactory.create(firstName, lastName);
        Trainer trainer = Trainer.builder()
                .user(created.user())
                .specialization(specialization)
                .build();

        trainerDao.save(trainer);
        gymMetrics.trainerRegistered();
        log.info("Created trainer profile with username: {}", created.user().getUsername());
        return created;
    }

    @Transactional(readOnly = true)
    public Trainer selectByUsername(String username) {
        return findProfileOrThrow(username);
    }

    @Transactional
    public Trainer updateProfile(String username, String firstName, String lastName, boolean isActive) {
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
}
