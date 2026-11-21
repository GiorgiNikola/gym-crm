package com.giorgi.workload.messaging;

import com.giorgi.workload.persistence.MonthDocument;
import com.giorgi.workload.persistence.TrainerWorkloadDocument;
import com.giorgi.workload.persistence.TrainerWorkloadMongoRepository;
import com.giorgi.workload.persistence.YearDocument;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
class WorkloadEventMongoEndToEndTest {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private TrainerWorkloadMongoRepository documents;

    private List<String> recentEventIds(String username) {
        return documents.findByUsername(username).map(TrainerWorkloadDocument::getRecentEventIds).orElse(List.of());
    }

    @Test
    @DisplayName("events from the broker end up as monthly totals in MongoDB and a repeated event id applies once")
    void storesEventsInMongoOnce() {
        JmsMessagingTemplate messaging = new JmsMessagingTemplate(jmsTemplate);
        String username = TestEvent.newUsername();
        UUID repeatedId = UUID.randomUUID();

        TestEvent.forTrainer(username).eventId(repeatedId).minutes(60).sendTo(messaging);
        TestEvent.forTrainer(username).trainingDate("2026-09-03").minutes(30).sendTo(messaging);
        TestEvent.forTrainer(username).action("DELETE").minutes(15).sendTo(messaging);
        TestEvent.forTrainer(username).eventId(repeatedId).minutes(60).sendTo(messaging);
        TestEvent last = TestEvent.forTrainer(username).trainingDate("2027-01-10").minutes(1).sendTo(messaging);

        Awaitility.await().until(() -> recentEventIds(username).contains(last.eventId().toString()));

        TrainerWorkloadDocument stored = documents.findByUsername(username).orElseThrow();
        Assertions.assertEquals(List.of(
                new YearDocument(2026, List.of(new MonthDocument(8, 45), new MonthDocument(9, 30))),
                new YearDocument(2027, List.of(new MonthDocument(1, 1)))), stored.getYears());
        Assertions.assertEquals(Boolean.TRUE, stored.getStatus());
        Assertions.assertEquals(3L, stored.getVersion());
        Assertions.assertEquals(1, stored.getRecentEventIds().stream()
                .filter(repeatedId.toString()::equals).count());
        Assertions.assertEquals(4, stored.getRecentEventIds().size());
    }
}
