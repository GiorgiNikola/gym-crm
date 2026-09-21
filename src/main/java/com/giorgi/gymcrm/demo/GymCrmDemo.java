package com.giorgi.gymcrm.demo;

import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.Trainer;
import com.giorgi.gymcrm.model.Training;
import com.giorgi.gymcrm.model.TrainingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@Profile("demo")
public class GymCrmDemo implements CommandLineRunner {
    private GymCrmFacade gymCrmFacade;

    @Autowired
    public void setGymCrmFacade(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @Override
    public void run(String... args) {
        Trainee seeded = gymCrmFacade.selectTraineeProfile(1L);
        log.info("Demo: seeded trainee 1 is {}", seeded.getUsername());

        Trainee john = gymCrmFacade.createTraineeProfile("John", "Smith", true,
                LocalDate.of(2001, 3, 12), "10 Pine Street Boston");
        log.info("Demo: second John Smith got username {} and a {} character password",
                john.getUsername(), john.getPassword().length());

        Trainer ana = gymCrmFacade.createTrainerProfile("Ana", "Kapanadze", true, TrainingType.YOGA);
        log.info("Demo: trainer Ana Kapanadze got username {}, a trainee already has Ana.Kapanadze",
                ana.getUsername());

        Training yoga = gymCrmFacade.createTrainingProfile(john.getUserID(), ana.getUserID(),
                "Evening Yoga", TrainingType.YOGA, LocalDate.of(2026, 9, 1), 45L);
        log.info("Demo: training {} booked for trainee {} with trainer {}",
                yoga.getID(), yoga.getTraineeID(), yoga.getTrainerID());

        Trainee changes = john.toBuilder()
                .address("22 Elm Street Boston")
                .username("Someone.Else")
                .build();
        Trainee updated = gymCrmFacade.updateTraineeProfile(changes);
        log.info("Demo: address is now {}, username is still {}", updated.getAddress(), updated.getUsername());

        Trainee nino = gymCrmFacade.createTraineeProfile("Nino", "Beridze", true, null, null);
        gymCrmFacade.deleteTraineeProfile(nino.getUserID());
        log.info("Demo: after deleting {}, looking up id {} returns {}",
                nino.getUsername(), nino.getUserID(), gymCrmFacade.selectTraineeProfile(nino.getUserID()));
    }
}
