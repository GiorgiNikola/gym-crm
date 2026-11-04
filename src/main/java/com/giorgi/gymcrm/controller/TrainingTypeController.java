package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.TrainingTypeResponse;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.web.Credentials;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Training types", description = "The training types the gym offers")
@RestController
@RequestMapping("/training-types")
public class TrainingTypeController {

    private final GymCrmFacade gymCrmFacade;

    public TrainingTypeController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @GetMapping
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Lists every training type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The training types"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong")
    })
    public List<TrainingTypeResponse> getTrainingTypes(@Parameter(hidden = true) Credentials credentials) {
        return gymCrmFacade.getTrainingTypes(credentials.username(), credentials.password()).stream()
                .map(TrainingTypeResponse::from)
                .toList();
    }
}
