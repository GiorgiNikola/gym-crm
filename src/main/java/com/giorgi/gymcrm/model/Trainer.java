package com.giorgi.gymcrm.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder(toBuilder = true)
public class Trainer extends User{
    private TrainingType specialization;
}
