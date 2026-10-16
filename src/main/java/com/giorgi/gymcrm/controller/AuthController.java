package com.giorgi.gymcrm.controller;

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

@Tag(name = "Authentication", description = "Checking credentials")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final GymCrmFacade gymCrmFacade;

    public AuthController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @GetMapping("/login")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Checks whether a username and password are valid")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The credentials are valid"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong")
    })
    public void login(@Parameter(hidden = true) Credentials credentials) {
        gymCrmFacade.login(credentials.username(), credentials.password());
    }
}
