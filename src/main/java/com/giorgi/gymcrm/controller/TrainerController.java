package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.dto.TrainerProfileResponse;
import com.giorgi.gymcrm.dto.TrainerRegistrationRequest;
import com.giorgi.gymcrm.dto.TrainerTrainingResponse;
import com.giorgi.gymcrm.dto.UpdateTrainerRequest;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
import java.util.List;

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

    @GetMapping("/{username}")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Returns a trainer profile with the assigned trainees")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The trainer profile"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "There is no trainer with that username")
    })
    public TrainerProfileResponse getProfile(@PathVariable String username,
                                             @Parameter(hidden = true) Credentials credentials) {
        RequestGuards.requireSelf(username, credentials);
        return TrainerProfileResponse.from(
                gymCrmFacade.selectTrainerProfile(username, credentials.password()));
    }

    @PutMapping("/{username}")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Replaces a trainer profile",
            description = "The specialization is read only: a specialization sent in the body is ignored.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated trainer profile"),
            @ApiResponse(responseCode = "400", description = "A name is missing or a field is too long"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "There is no trainer with that username")
    })
    public TrainerProfileResponse updateProfile(@PathVariable String username,
                                                @Parameter(hidden = true) Credentials credentials,
                                                @Valid @RequestBody UpdateTrainerRequest request) {
        RequestGuards.requireSelf(username, credentials);
        return TrainerProfileResponse.from(gymCrmFacade.updateTrainerProfile(
                username, credentials.password(), request.firstName(), request.lastName(), request.isActive()));
    }

    @GetMapping("/{username}/trainings")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Lists the trainings of a trainer, oldest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The matching trainings"),
            @ApiResponse(responseCode = "400", description = "from is after to"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user")
    })
    public List<TrainerTrainingResponse> getTrainings(
            @PathVariable String username,
            @Parameter(hidden = true) Credentials credentials,
            @Parameter(description = "Earliest training date to include, as yyyy-MM-dd", example = "2026-08-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Latest training date to include, as yyyy-MM-dd", example = "2026-08-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Part of the trainee's first name, last name or full name, "
                    + "case insensitive", example = "Smith")
            @RequestParam(required = false) String traineeName) {
        RequestGuards.requireSelf(username, credentials);

        return gymCrmFacade.getTrainerTrainings(username, credentials.password(), from, to, traineeName)
                .stream()
                .map(TrainerTrainingResponse::from)
                .toList();
    }
}
