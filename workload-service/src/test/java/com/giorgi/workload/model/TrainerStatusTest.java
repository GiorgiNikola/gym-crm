package com.giorgi.workload.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrainerStatusTest {

    @Test
    @DisplayName("an active flag maps to ACTIVE")
    void mapsTrueToActive() {
        Assertions.assertEquals(TrainerStatus.ACTIVE, TrainerStatus.of(true));
    }

    @Test
    @DisplayName("an inactive flag maps to INACTIVE")
    void mapsFalseToInactive() {
        Assertions.assertEquals(TrainerStatus.INACTIVE, TrainerStatus.of(false));
    }
}
