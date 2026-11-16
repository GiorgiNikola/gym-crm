package com.giorgi.gymcrm.workload;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkloadChangeListenerTest {

    @Test
    @DisplayName("a committed change goes to the gateway")
    void forwardsTheChange() {
        WorkloadGateway gateway = mock(WorkloadGateway.class);
        WorkloadRequest change = new WorkloadRequest("Sarah.Miller", "Sarah", "Miller", true,
                LocalDate.of(2026, 8, 1), 60, ActionType.DELETE);

        new WorkloadChangeListener(gateway).onWorkloadChanged(new WorkloadChangedEvent(change));

        verify(gateway).send(change);
    }

    @Test
    @DisplayName("the listener only runs after the commit")
    void runsAfterCommit() throws NoSuchMethodException {
        TransactionalEventListener listener = WorkloadChangeListener.class
                .getMethod("onWorkloadChanged", WorkloadChangedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        Assertions.assertEquals(TransactionPhase.AFTER_COMMIT, listener.phase());
        Assertions.assertFalse(listener.fallbackExecution());
    }
}
