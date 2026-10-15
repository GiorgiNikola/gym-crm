package com.giorgi.gymcrm.dto;

import com.giorgi.gymcrm.model.TrainingType;

public record TrainingTypeResponse(Long id, String name) {

    public static TrainingTypeResponse from(TrainingType type) {
        return new TrainingTypeResponse(type.getId(), type.getName());
    }
}
