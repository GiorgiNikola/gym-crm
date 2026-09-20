package com.giorgi.gymcrm.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
public class Trainee extends User{
    private LocalDate dateOfBirth;
    private String address;
}
