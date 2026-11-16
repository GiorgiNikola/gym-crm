package com.giorgi.workload.controller;

import com.giorgi.workload.dto.MonthlyWorkloadResponse;
import com.giorgi.workload.dto.TrainerWorkloadResponse;
import com.giorgi.workload.dto.WorkloadRequest;
import com.giorgi.workload.service.WorkloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.hibernate.validator.constraints.Range;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Workloads", description = "Monthly training time of trainers, in minutes")
@RestController
@RequestMapping("/workloads")
public class WorkloadController {

    private final WorkloadService workloadService;

    public WorkloadController(WorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    @PostMapping
    @Operation(summary = "Records one workload change for a trainer",
            description = "ADD adds the training duration to the month of the training date, DELETE "
                    + "subtracts it and never goes below zero. The names and the status in the body "
                    + "replace the stored ones. A DELETE for a trainer with no recorded workload is "
                    + "accepted and changes nothing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The change was accepted"),
            @ApiResponse(responseCode = "400", description = "A field is missing, blank, not positive, "
                    + "or the body is not readable",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public void recordChange(@Valid @RequestBody WorkloadRequest request) {
        workloadService.apply(request.toChange());
    }

    @GetMapping("/{username}")
    @Operation(summary = "Returns the full workload summary of a trainer",
            description = "Years and months are in ascending order. A month without training time is "
                    + "not listed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The workload summary"),
            @ApiResponse(responseCode = "404", description = "There is no workload for that trainer",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public TrainerWorkloadResponse getWorkload(
            @Parameter(description = "The username of the trainer", example = "Sarah.Miller")
            @PathVariable String username) {
        return TrainerWorkloadResponse.from(workloadService.getWorkload(username));
    }

    @GetMapping("/{username}/{year}/{month}")
    @Operation(summary = "Returns the training time of a trainer in one month",
            description = "The duration is 0 when the trainer is known but has nothing in that month.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The training time in that month"),
            @ApiResponse(responseCode = "400", description = "The month is not between 1 and 12, or the "
                    + "year or the month is not a number",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "There is no workload for that trainer",
                    content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public MonthlyWorkloadResponse getMonthlyWorkload(
            @Parameter(description = "The username of the trainer", example = "Sarah.Miller")
            @PathVariable String username,
            @Parameter(description = "The calendar year", example = "2026")
            @PathVariable int year,
            @Parameter(description = "The month, from 1 to 12", example = "8")
            @PathVariable @Range(min = 1, max = 12) int month) {
        return new MonthlyWorkloadResponse(username, year, month,
                workloadService.getMonthlyDuration(username, year, month));
    }
}
