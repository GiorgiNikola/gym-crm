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
    @DisplayName("a committed change goes to the publisher with its event id")
    void forwardsTheEvent() {
        WorkloadPublisher publisher = mock(WorkloadPublisher.class);
        WorkloadChangedEvent event = WorkloadChangedEvent.of(new WorkloadChange("Sarah.Miller", "Sarah", "Miller",
                true, LocalDate.of(2026, 8, 1), 60, ActionType.DELETE));

        new WorkloadChangeListener(publisher).onWorkloadChanged(event);

        verify(publisher).publish(event);
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
