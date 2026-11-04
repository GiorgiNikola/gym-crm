package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.dto.TraineeProfileResponse;
import com.giorgi.gymcrm.dto.TraineeRegistrationRequest;
import com.giorgi.gymcrm.dto.TraineeTrainingResponse;
import com.giorgi.gymcrm.dto.TrainerSummary;
import com.giorgi.gymcrm.dto.UpdateTraineeRequest;
import com.giorgi.gymcrm.dto.UpdateTraineeTrainersRequest;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.model.Trainee;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

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

    @GetMapping("/{username}/unassigned-trainers")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Lists the trainers this trainee could still pick",
            description = "Only active trainers that are not assigned to the trainee yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The trainers, ordered by username"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "There is no trainee with that username")
    })
    public List<TrainerSummary> getUnassignedTrainers(@PathVariable String username,
                                                      @Parameter(hidden = true) Credentials credentials) {
        RequestGuards.requireSelf(username, credentials);
        return gymCrmFacade.getUnassignedTrainers(username, credentials.password()).stream()
                .map(TrainerSummary::from)
                .toList();
    }

    @PutMapping("/{username}/trainers")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Replaces the trainers assigned to a trainee",
            description = "The list replaces the current assignments: an empty list removes every trainer, "
                    + "and sending the same list again leaves the same state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The resulting trainers, sorted by username"),
            @ApiResponse(responseCode = "400", description = "The list is missing or an entry is blank"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "The trainee or one of the trainers does not exist")
    })
    public List<TrainerSummary> updateTrainers(@PathVariable String username,
                                               @Parameter(hidden = true) Credentials credentials,
                                               @Valid @RequestBody UpdateTraineeTrainersRequest request) {
        RequestGuards.requireSelf(username, credentials);

        Trainee updated = gymCrmFacade.updateTraineeTrainers(username, credentials.password(),
                new LinkedHashSet<>(request.trainerUsernames()));

        return updated.getTrainers().stream()
                .map(TrainerSummary::from)
                .sorted(Comparator.comparing(TrainerSummary::username))
                .toList();
    }

    @GetMapping("/{username}/trainings")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Lists the trainings of a trainee, oldest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching trainings"),
            @ApiResponse(responseCode = "400", description = "from is after to, or the training type is unknown"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user")
    })
    public List<TraineeTrainingResponse> getTrainings(
            @PathVariable String username,
            @Parameter(hidden = true) Credentials credentials,
            @Parameter(description = "Earliest training date to include, as yyyy-MM-dd", example = "2026-08-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Latest training date to include, as yyyy-MM-dd", example = "2026-08-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Part of the trainer's first name, last name or full name, "
                    + "case insensitive", example = "Miller")
            @RequestParam(required = false) String trainerName,
            @Parameter(description = "An exact training type name, as listed by GET /training-types",
                    example = "YOGA")
            @RequestParam(required = false) String trainingType) {
        RequestGuards.requireSelf(username, credentials);

        return gymCrmFacade.getTraineeTrainings(username, credentials.password(), from, to, trainerName, trainingType)
                .stream()
                .map(TraineeTrainingResponse::from)
                .toList();
    }
}
