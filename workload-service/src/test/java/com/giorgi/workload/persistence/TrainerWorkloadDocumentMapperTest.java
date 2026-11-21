package com.giorgi.workload.persistence;

import com.giorgi.workload.model.MonthSummary;
import com.giorgi.workload.model.TrainerWorkload;
import com.giorgi.workload.model.YearSummary;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class TrainerWorkloadDocumentMapperTest {

    private final TrainerWorkloadDocumentMapper mapper = new TrainerWorkloadDocumentMapper();

    @Test
    @DisplayName("a stored document becomes the domain workload with every year and month")
    void mapsDocumentToDomain() {
        TrainerWorkloadDocument document = new TrainerWorkloadDocument(new ObjectId(), "Sarah.Miller", "Sarah",
                "Miller", false, List.of(
                        new YearDocument(2026, List.of(new MonthDocument(8, 60), new MonthDocument(9, 45))),
                        new YearDocument(2027, List.of(new MonthDocument(1, 30)))),
                List.of("3f1c9a52-7d0e-4b8a-9c61-2e5f8d4a7b10"), 3L);

        TrainerWorkload workload = mapper.toDomain(document);

        Assertions.assertEquals(new TrainerWorkload("Sarah.Miller", "Sarah", "Miller", false, List.of(
                new YearSummary(2026, List.of(new MonthSummary(8, 60), new MonthSummary(9, 45))),
                new YearSummary(2027, List.of(new MonthSummary(1, 30))))), workload);
    }

    @Test
    @DisplayName("a domain workload becomes a new document without id, version or event ids")
    void mapsDomainToNewDocument() {
        TrainerWorkload workload = new TrainerWorkload("Sarah.Miller", "Sarah", "Miller", true, List.of(
                new YearSummary(2026, List.of(new MonthSummary(8, 150)))));

        TrainerWorkloadDocument document = mapper.toDocument(workload);

        Assertions.assertNull(document.getId());
        Assertions.assertNull(document.getVersion());
        Assertions.assertEquals(List.of(), document.getRecentEventIds());
        Assertions.assertEquals("Sarah.Miller", document.getUsername());
        Assertions.assertEquals("Sarah", document.getFirstName());
        Assertions.assertEquals("Miller", document.getLastName());
        Assertions.assertEquals(Boolean.TRUE, document.getStatus());
        Assertions.assertEquals(List.of(new YearDocument(2026, List.of(new MonthDocument(8, 150)))),
                document.getYears());
    }

    @Test
    @DisplayName("mapping there and back keeps the workload unchanged")
    void roundTripsWorkload() {
        TrainerWorkload workload = new TrainerWorkload("Mike.Brown", "Mike", "Brown", false, List.of(
                new YearSummary(2025, List.of(new MonthSummary(12, 20))),
                new YearSummary(2026, List.of(new MonthSummary(1, 40), new MonthSummary(2, 0)))));

        Assertions.assertEquals(workload, mapper.toDomain(mapper.toDocument(workload)));
    }
}
