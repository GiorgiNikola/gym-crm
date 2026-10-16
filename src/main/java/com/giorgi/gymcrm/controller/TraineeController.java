package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.dto.TraineeRegistrationRequest;
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

@Tag(name = "Trainees", description = "Trainee profiles")
@RestController
@RequestMapping("/trainees")
public class TraineeController {

    private final GymCrmFacade gymCrmFacade;

    public TraineeController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @PostMapping
    @Operation(summary = "Registers a trainee and hands back the generated credentials")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The trainee was registered"),
            @ApiResponse(responseCode = "400", description = "A name is missing or a field is too long")
    })
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody TraineeRegistrationRequest request) {
        User user = gymCrmFacade.createTraineeProfile(
                request.firstName(), request.lastName(), request.dateOfBirth(), request.address()).getUser();

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{username}")
                .buildAndExpand(user.getUsername())
                .toUri();

        return ResponseEntity.created(location)
                .body(new RegistrationResponse(user.getUsername(), user.getPassword()));
    }
}
