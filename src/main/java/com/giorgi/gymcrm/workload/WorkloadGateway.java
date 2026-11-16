package com.giorgi.gymcrm.workload;

import com.giorgi.gymcrm.exception.WorkloadUnavailableException;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import java.time.YearMonth;

@Slf4j
@Component
public class WorkloadGateway {

    static final String BREAKER = "workload";

    private final WorkloadClient client;
    private final CircuitBreaker breaker;

    public WorkloadGateway(WorkloadClient client, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.client = client;
        this.breaker = circuitBreakerFactory.create(BREAKER);
    }

    public void send(WorkloadRequest change) {
        breaker.run(() -> {
            client.recordChange(change);
            log.debug("Sent {} of {} minutes for trainer {}", change.actionType(), change.trainingDuration(),
                    change.trainerUsername());
            return null;
        }, failure -> {
            log.warn("Workload change not sent: transactionId={} action={} trainer={} month={} minutes={} reason={}",
                    transactionId(), change.actionType(), change.trainerUsername(),
                    YearMonth.from(change.trainingDate()), change.trainingDuration(), reason(failure));
            return null;
        });
    }

    public long monthlyMinutes(String username, int year, int month) {
        return breaker.run(() -> fetchMonthlyMinutes(username, year, month), failure -> {
            log.warn("Workload not read: transactionId={} trainer={} month={} reason={}",
                    transactionId(), username, YearMonth.of(year, month), reason(failure));
            throw new WorkloadUnavailableException(failure);
        });
    }

    private long fetchMonthlyMinutes(String username, int year, int month) {
        try {
            return client.getMonthlyWorkload(username, year, month).trainingSummaryDuration();
        } catch (HttpClientErrorException.NotFound unknownTrainer) {
            return 0;
        }
    }

    private static String transactionId() {
        return MDC.get(TransactionIdFilter.MDC_KEY);
    }

    private static String reason(Throwable failure) {
        if (failure instanceof CallNotPermittedException) {
            return "circuit breaker open";
        }
        if (failure instanceof RestClientResponseException response) {
            return "status " + response.getStatusCode().value();
        }
        return NestedExceptionUtils.getMostSpecificCause(failure).getClass().getSimpleName();
    }
}
