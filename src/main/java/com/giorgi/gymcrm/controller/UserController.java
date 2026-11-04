package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.ChangePasswordRequest;
import com.giorgi.gymcrm.dto.StatusRequest;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.web.Credentials;
import com.giorgi.gymcrm.web.RequestGuards;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Password and activation state")
@RestController
@RequestMapping("/users")
public class UserController {

    private final GymCrmFacade gymCrmFacade;

    public UserController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @PutMapping("/{username}/password")
    @Operation(summary = "Changes a user password",
            description = "Authenticates with the old password from the request body, so this call sends no "
                    + "Authorization header.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The password was changed"),
            @ApiResponse(responseCode = "400", description = "A password is blank or the body is malformed"),
            @ApiResponse(responseCode = "401", description = "The old password is wrong"),
            @ApiResponse(responseCode = "404", description = "There is no user with that username")
    })
    public void changePassword(@PathVariable String username,
                               @Valid @RequestBody ChangePasswordRequest request) {
        gymCrmFacade.changePassword(username, request.oldPassword(), request.newPassword());
    }

    @PatchMapping("/{username}/status")
    @SecurityRequirement(name = "basicAuth")
    @Operation(summary = "Activates or deactivates a user",
            description = "Deliberately not idempotent: asking for the state the user is already in returns 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The state was changed"),
            @ApiResponse(responseCode = "400", description = "isActive is missing or the body is malformed"),
            @ApiResponse(responseCode = "401", description = "The credentials are missing, malformed or wrong"),
            @ApiResponse(responseCode = "403", description = "The credentials belong to another user"),
            @ApiResponse(responseCode = "404", description = "There is no user with that username"),
            @ApiResponse(responseCode = "409", description = "The user is already in that state")
    })
    public void changeStatus(@PathVariable String username,
                             @Parameter(hidden = true) Credentials credentials,
                             @Valid @RequestBody StatusRequest request) {
        RequestGuards.requireSelf(username, credentials);
        gymCrmFacade.changeStatus(username, credentials.password(), request.isActive());
    }
}
