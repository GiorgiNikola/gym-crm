package com.giorgi.gymcrm.component;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

public class ComponentHooks {

    private final WorkloadQueue queue;
    private final WorkloadServiceStub workloadService;
    private final CircuitBreakerRegistry breakers;
    private final JdbcTemplate jdbcTemplate;

    public ComponentHooks(WorkloadQueue queue, WorkloadServiceStub workloadService,
                          CircuitBreakerRegistry breakers, JdbcTemplate jdbcTemplate) {
        this.queue = queue;
        this.workloadService = workloadService;
        this.breakers = breakers;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Before
    public void startClean() throws Exception {
        queue.drain();
        workloadService.reset();
        breakers.circuitBreaker("workload").reset();
    }

    @After
    public void removeAccounts() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }
}
