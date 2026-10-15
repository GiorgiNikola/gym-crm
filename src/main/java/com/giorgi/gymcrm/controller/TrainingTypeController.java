package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.dto.TrainingTypeResponse;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/training-types")
public class TrainingTypeController {

    private final GymCrmFacade gymCrmFacade;

    public TrainingTypeController(GymCrmFacade gymCrmFacade) {
        this.gymCrmFacade = gymCrmFacade;
    }

    @GetMapping
    public List<TrainingTypeResponse> getTrainingTypes() {
        return gymCrmFacade.getTrainingTypes().stream()
                .map(TrainingTypeResponse::from)
                .toList();
    }
}
