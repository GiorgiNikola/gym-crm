package com.giorgi.gymcrm.demo;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Profile("demo")
public class DemoRunner implements CommandLineRunner {

    private final GymCrmFacade facade;

    public DemoRunner(GymCrmFacade facade) {
        this.facade = facade;
    }

    @Override
    public void run(String... args) {
        log.info("=== 1, 2. Create profiles ===");
        Trainer sarah = facade.createTrainerProfile("Sarah", "Miller", "YOGA");
        Trainer robert = facade.createTrainerProfile("Robert", "Taylor", "FITNESS");
        Trainee john = facade.createTraineeProfile("John", "Smith",
                LocalDate.of(1998, 5, 14), "123 Main St New York");

        String johnUsername = john.getUser().getUsername();
        String johnPassword = john.getUser().getPassword();
        String sarahUsername = sarah.getUser().getUsername();
        String sarahPassword = sarah.getUser().getPassword();

        log.info("=== Username collision check ===");
        Trainee secondJohn = facade.createTraineeProfile("John", "Smith", null, null);
        log.info("Second John.Smith resolved to: {}", secondJohn.getUser().getUsername());

        log.info("=== 5, 6. Select profiles by username ===");
        Trainee loadedJohn = facade.selectTraineeProfile(johnUsername, johnPassword);
        log.info("Trainee: {} {}", loadedJohn.getUser().getFirstName(), loadedJohn.getUser().getLastName());
        log.info("Trainer specialization: {}", facade.selectTrainerProfile(sarahUsername, sarahPassword)
                .getSpecialization().getName());

        log.info("=== 17. Trainers not assigned to trainee ===");
        List<Trainer> unassigned = facade.getUnassignedTrainers(johnUsername, johnPassword);
        log.info("Unassigned trainers: {}", unassigned.size());

        log.info("=== 18. Update trainee's trainers list ===");
        facade.updateTraineeTrainers(johnUsername, johnPassword,
                Set.of(sarahUsername, robert.getUser().getUsername()));
        log.info("After assignment, unassigned trainers: {}",
                facade.getUnassignedTrainers(johnUsername, johnPassword).size());

        log.info("=== 16. Add trainings ===");
        facade.addTraining(johnUsername, johnPassword, johnUsername, sarahUsername,
                "Morning Yoga", "YOGA", LocalDate.of(2026, 8, 1), 60L);
        facade.addTraining(johnUsername, johnPassword, johnUsername,
                robert.getUser().getUsername(),
                "Strength Session", "FITNESS", LocalDate.of(2026, 8, 15), 45L);

        log.info("=== Specialization mismatch is rejected ===");
        try {
            facade.addTraining(johnUsername, johnPassword, johnUsername, sarahUsername,
                    "Zumba Class", "ZUMBA", LocalDate.of(2026, 8, 20), 50L);
        } catch (IllegalArgumentException e) {
            log.info("Rejected as expected: {}", e.getMessage());
        }

        log.info("=== 14. Trainee trainings, unfiltered then filtered ===");
        log.info("All: {}", facade.getTraineeTrainings(johnUsername, johnPassword,
                null, null, null, null).size());
        log.info("Yoga only: {}", facade.getTraineeTrainings(johnUsername, johnPassword,
                null, null, null, "YOGA").size());
        log.info("From August 10: {}", facade.getTraineeTrainings(johnUsername, johnPassword,
                LocalDate.of(2026, 8, 10), null, null, null).size());

        log.info("=== 15. Trainer trainings ===");
        List<Training> sarahTrainings = facade.getTrainerTrainings(sarahUsername, sarahPassword,
                null, null, null);
        log.info("Sarah's trainings: {}", sarahTrainings.size());

        log.info("=== 9, 10. Update profiles ===");
        facade.updateTraineeProfile(johnUsername, johnPassword, "John", "Smith",
                LocalDate.of(1998, 5, 14), "456 Park Avenue Boston");
        facade.updateTrainerProfile(sarahUsername, sarahPassword, "Sarah", "Miller", "STRETCHING");
        log.info("Sarah's specialization now: {}", facade.selectTrainerProfile(sarahUsername, sarahPassword)
                .getSpecialization().getName());

        log.info("=== 11, 12. Activate and deactivate ===");
        facade.deactivateTrainee(johnUsername, johnPassword);
        log.info("Trainee active: {}", facade.selectTraineeProfile(johnUsername, johnPassword)
                .getUser().isActive());
        facade.activateTrainee(johnUsername, johnPassword);
        try {
            facade.activateTrainee(johnUsername, johnPassword);
        } catch (IllegalArgumentException e) {
            log.info("Non-idempotent as expected: {}", e.getMessage());
        }

        log.info("=== 7, 8. Change passwords ===");
        facade.changeTraineePassword(johnUsername, johnPassword, "newPassword1");
        facade.selectTraineeProfile(johnUsername, "newPassword1");
        log.info("Trainee password changed and re-authenticated");

        log.info("=== 3, 4. Authentication failure ===");
        try {
            facade.selectTraineeProfile(johnUsername, johnPassword);
        } catch (RuntimeException e) {
            log.info("Old password rejected as expected: {}", e.getMessage());
        }

        log.info("=== 13. Delete trainee, cascade to trainings ===");
        facade.deleteTraineeProfile(johnUsername, "newPassword1");
        log.info("Sarah's trainings after trainee delete: {}",
                facade.getTrainerTrainings(sarahUsername, sarahPassword, null, null, null).size());

        log.info("=== Demo complete ===");
    }
}