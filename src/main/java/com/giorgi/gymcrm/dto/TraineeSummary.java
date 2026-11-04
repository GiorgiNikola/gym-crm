package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.Trainee;
import com.giorgi.gymcrm.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record TraineeSummary(

        @Schema(description = "The username of the trainee", example = "John.Smith")
        String username,

        @Schema(description = "The first name of the trainee", example = "John")
        String firstName,

        @Schema(description = "The last name of the trainee", example = "Smith")
        String lastName) {

    public static TraineeSummary from(Trainee trainee) {
        User user = trainee.getUser();
        return new TraineeSummary(user.getUsername(), user.getFirstName(), user.getLastName());
    }
}
