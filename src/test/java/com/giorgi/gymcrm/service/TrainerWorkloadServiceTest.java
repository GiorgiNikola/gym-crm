package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.workload.WorkloadGateway;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainerWorkloadServiceTest {

    @Mock
    private TrainerService trainerService;

    @Mock
    private WorkloadGateway workloadGateway;

    @InjectMocks
    private TrainerWorkloadService trainerWorkloadService;

    @Test
    @DisplayName("a known trainer's minutes come from workload-service")
    void readsTheMinutesOfAKnownTrainer() {
        when(workloadGateway.monthlyMinutes("Sarah.Miller", 2026, 8)).thenReturn(150L);

        Assertions.assertEquals(150L, trainerWorkloadService.getMonthlyMinutes("Sarah.Miller", 2026, 8));
        verify(trainerService).requireExists("Sarah.Miller");
    }

    @Test
    @DisplayName("an unknown trainer is a missing profile and workload-service is not asked")
    void rejectsAnUnknownTrainer() {
        doThrow(new ProfileNotFoundException("Trainer not found: John.Smith"))
                .when(trainerService).requireExists("John.Smith");

        ProfileNotFoundException thrown = Assertions.assertThrows(ProfileNotFoundException.class,
                () -> trainerWorkloadService.getMonthlyMinutes("John.Smith", 2026, 8));

        Assertions.assertEquals("Trainer not found: John.Smith", thrown.getMessage());
        verifyNoInteractions(workloadGateway);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 13, -1})
    @DisplayName("a month outside 1 to 12 is rejected before anything is looked up")
    void rejectsAnImpossibleMonth(int month) {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainerWorkloadService.getMonthlyMinutes("Sarah.Miller", 2026, month));

        Assertions.assertEquals("Month must be between 1 and 12", thrown.getMessage());
        verifyNoInteractions(trainerService, workloadGateway);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 12})
    @DisplayName("January and December are valid months")
    void acceptsTheEdgeMonths(int month) {
        Assertions.assertDoesNotThrow(() -> trainerWorkloadService.getMonthlyMinutes("Sarah.Miller", 2026, month));
    }
}
