package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.dto.TraineeProfileResponse;
import com.giorgi.gymcrm.dto.TraineeRegistrationRequest;
import com.giorgi.gymcrm.dto.UpdateTraineeRequest;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.User;
import com.giorgi.gymcrm.web.Credentials;
import com.giorgi.gymcrm.web.RequestGuards;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/{username}")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Returns a trainee profile with the assigned trainers")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The trainee profile"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "There is no trainee with that username")
    })
    public TraineeProfileResponse getProfile(@PathVariable String username,
                                             @Parameter(hidden = true) Credentials credentials) {
        RequestGuards.requireSelf(username, credentials);
        return TraineeProfileResponse.from(
                gymCrmFacade.selectTraineeProfile(username, credentials.password()));
    }

    @PutMapping("/{username}")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Replaces a trainee profile",
            description = "A date of birth or an address left out of the body clears the stored value. "
                    + "isActive is applied as it is, so sending the current state again is accepted, "
                    + "unlike PATCH /users/{username}/status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated trainee profile"),
            @ApiResponse(responseCode = "400", description = "A name is missing or a field is too long"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "There is no trainee with that username")
    })
    public TraineeProfileResponse updateProfile(@PathVariable String username,
                                                @Parameter(hidden = true) Credentials credentials,
                                                @Valid @RequestBody UpdateTraineeRequest request) {
        RequestGuards.requireSelf(username, credentials);
        return TraineeProfileResponse.from(gymCrmFacade.updateTraineeProfile(
                username, credentials.password(), request.firstName(), request.lastName(),
                request.dateOfBirth(), request.address(), request.isActive()));
    }

    @DeleteMapping("/{username}")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Deletes a trainee profile",
            description = "The trainee's trainings go with it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The trainee was deleted"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "There is no trainee with that username")
    })
    public void deleteProfile(@PathVariable String username,
                              @Parameter(hidden = true) Credentials credentials) {
        RequestGuards.requireSelf(username, credentials);
        gymCrmFacade.deleteTraineeProfile(username, credentials.password());
    }
}
