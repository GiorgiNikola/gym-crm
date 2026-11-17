package com.giorgi.gymcrm.workload;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;

public interface WorkloadClient {

    @GetExchange("/workloads/{username}/{year}/{month}")
    MonthlyWorkloadResponse getMonthlyWorkload(@PathVariable String username,
                                               @PathVariable int year,
                                               @PathVariable int month);
}
