package com.giorgi.gymcrm.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
public abstract class User {
    private long userID;
    private String firstName;
    private String lastName;
    private String username;
    private String password;
    private boolean isActive;
}
