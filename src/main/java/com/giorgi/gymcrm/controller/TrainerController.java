package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.dto.TrainerRegistrationRequest;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Tag(name = "Trainers", description = "Trainer profiles")
@RestController
@RequestMapping("/trainers")
public class TrainerController {

    private final GymCrmFacade gymCrmFacade;

    public TrainerController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @PostMapping
    @Operation(summary = "Registers a trainer and hands back the generated credentials",
            description = "The specialization is a training type name, as listed by GET /training-types.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The trainer was registered"),
            @ApiResponse(responseCode = "400", description = "A field is missing, too long, or the "
                    + "specialization is not a known training type")
    })
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody TrainerRegistrationRequest request) {
        User user = gymCrmFacade.createTrainerProfile(
                request.firstName(), request.lastName(), request.specialization()).getUser();

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{username}")
                .buildAndExpand(user.getUsername())
                .toUri();

        return ResponseEntity.created(location)
                .body(new RegistrationResponse(user.getUsername(), user.getPassword()));
    }
}
