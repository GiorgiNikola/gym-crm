package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.AddTrainingRequest;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.web.Credentials;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Trainings", description = "Logging trainings")
@RestController
@RequestMapping("/trainings")
public class TrainingController {

    private final GymCrmFacade gymCrmFacade;

    public TrainingController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @PostMapping
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Logs a training for a trainee and a trainer",
            description = "The training type is not sent: it comes from the trainer's specialization. "
                    + "The duration is in minutes. The credentials only have to belong to some existing "
                    + "user, so any authenticated user may log a training for any trainee and trainer pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The training was logged"),
            @ApiResponse(responseCode = "400", description = "A field is missing, blank or not positive"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "404", description = "The trainee or the trainer does not exist")
    })
    public void addTraining(@Parameter(hidden = true) Credentials credentials,
                            @Valid @RequestBody AddTrainingRequest request) {
        gymCrmFacade.addTraining(credentials.username(), credentials.password(),
                request.traineeUsername(), request.trainerUsername(), request.trainingName(),
                request.trainingDate(), request.trainingDuration());
    }
}
