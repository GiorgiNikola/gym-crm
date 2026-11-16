package com.giorgi.gymcrm.workload;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WorkloadChangeListener {

    private final WorkloadGateway gateway;

    public WorkloadChangeListener(WorkloadGateway gateway) {
        this.gateway = gateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkloadChanged(WorkloadChangedEvent event) {
        gateway.send(event.change());
    }
}
