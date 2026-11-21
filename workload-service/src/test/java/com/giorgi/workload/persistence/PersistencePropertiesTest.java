package com.giorgi.workload.persistence;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersistencePropertiesTest {

    @Test
    @DisplayName("a trainer must keep at least one recent event id")
    void refusesEmptyEventIdLimit() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PersistenceProperties(0));

        Assertions.assertEquals("workload.persistence.recent-event-ids must be at least 1", thrown.getMessage());
        Assertions.assertEquals(1, new PersistenceProperties(1).recentEventIds());
    }
}
