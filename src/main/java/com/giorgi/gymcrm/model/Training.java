package com.giorgi.gymcrm.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder(toBuilder = true)
public class Training {
    private long ID;
    private long traineeID;
    private long trainerID;
    private String name;
    private TrainingType type;
    private LocalDate date;
    private long duration;
}
