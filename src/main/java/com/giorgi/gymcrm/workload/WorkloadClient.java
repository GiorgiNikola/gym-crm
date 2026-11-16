package com.giorgi.gymcrm.workload;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

public interface WorkloadClient {

    @PostExchange("/workloads")
    void recordChange(@RequestBody WorkloadRequest change);

    @GetExchange("/workloads/{username}/{year}/{month}")
    MonthlyWorkloadResponse getMonthlyWorkload(@PathVariable String username,
                                               @PathVariable int year,
                                               @PathVariable int month);
}
