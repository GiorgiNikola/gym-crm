package com.giorgi.gymcrm.workload;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkloadChangeListener {

    private final WorkloadPublisher publisher;

    public WorkloadChangeListener(WorkloadPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkloadChanged(WorkloadChangedEvent event) {
        publisher.publish(event);
    }
}
