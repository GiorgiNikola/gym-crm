package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.LoginRequest;
import com.giorgi.gymcrm.dto.TokenResponse;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Getting a token")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final GymCrmFacade gymCrmFacade;

    public AuthController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchanges a username and password for a token",
            description = "An unknown username and a wrong password give the same 401. Three failures in a row "
                    + "lock the username for five minutes, and a locked one is answered with 429 and Retry-After "
                    + "without the password being looked at. Logging out is POST /auth/logout, which Spring "
                    + "Security answers from its filter chain rather than from a controller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A signed token"),
            @ApiResponse(responseCode = "400", description = "The username or the password is blank"),
            @ApiResponse(responseCode = "401", description = "The credentials are wrong"),
            @ApiResponse(responseCode = "429", description = "The username is locked after too many failures")
    })
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return new TokenResponse(gymCrmFacade.login(request.username(), request.password()));
    }
}
