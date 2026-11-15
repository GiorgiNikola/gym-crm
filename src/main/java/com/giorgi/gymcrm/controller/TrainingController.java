package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.AddTrainingRequest;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("#request.traineeUsername() == authentication.name"
            + " or #request.trainerUsername() == authentication.name")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logs a training for a trainee and a trainer",
            description = "The training type is not sent: it comes from the trainer's specialization. The "
                    + "duration is in minutes. The caller has to be one of the two people in the body, so "
                    + "nobody can log a training between two strangers.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The training was logged"),
            @ApiResponse(responseCode = "400", description = "A field is missing, blank or not positive"),
            @ApiResponse(responseCode = "401", description = "The token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "The caller is neither the trainee nor the trainer"),
            @ApiResponse(responseCode = "404", description = "The trainee or the trainer does not exist")
    })
    public void addTraining(@Valid @RequestBody AddTrainingRequest request) {
        gymCrmFacade.addTraining(request.traineeUsername(), request.trainerUsername(),
                request.trainingName(), request.trainingDate(), request.trainingDuration());
    }
}
