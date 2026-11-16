package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.workload.WorkloadGateway;
import org.springframework.stereotype.Service;

@Service
public class TrainerWorkloadService {

    private final TrainerService trainerService;
    private final WorkloadGateway workloadGateway;

    public TrainerWorkloadService(TrainerService trainerService, WorkloadGateway workloadGateway) {
        this.trainerService = trainerService;
        this.workloadGateway = workloadGateway;
    }

    public long getMonthlyMinutes(String username, int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        trainerService.requireExists(username);
        return workloadGateway.monthlyMinutes(username, year, month);
    }
}
