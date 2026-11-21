package com.giorgi.workload.messaging;

import com.giorgi.workload.model.WorkloadChange;
import com.giorgi.workload.service.WorkloadService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "workload.messaging.concurrency=2-2")
@ActiveProfiles("test")
class WorkloadEventOrderingIntegrationTest {

    private static final int EVENTS_PER_TRAINER = 20;

    @MockitoSpyBean
    private WorkloadService workloadService;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private JmsListenerEndpointRegistry listenerRegistry;

    private record Applied(String username, int minutes, String thread) {
    }

    private List<Applied> appliedTo(Queue<Applied> applied, String username) {
        return applied.stream().filter(entry -> entry.username().equals(username)).toList();
    }

    @Test
    @DisplayName("with two consumers, interleaved events of two trainers apply in send order per trainer")
    void keepsPerTrainerOrderAcrossConsumers() {
        Queue<Applied> applied = new ConcurrentLinkedQueue<>();
        doAnswer(invocation -> {
            WorkloadChange change = invocation.getArgument(0);
            applied.add(new Applied(change.trainerUsername(), change.trainingDuration(),
                    Thread.currentThread().getName()));
            return invocation.callRealMethod();
        }).when(workloadService).apply(any(), any());

        DefaultMessageListenerContainer container = (DefaultMessageListenerContainer)
                listenerRegistry.getListenerContainers().iterator().next();
        Assertions.assertEquals(2, container.getConcurrentConsumers());

        JmsMessagingTemplate messaging = new JmsMessagingTemplate(jmsTemplate);
        String first = TestEvent.newUsername();
        String second = TestEvent.newUsername();
        List<Integer> sequence = IntStream.rangeClosed(1, EVENTS_PER_TRAINER).boxed().toList();
        for (int minutes : sequence) {
            TestEvent.forTrainer(first).minutes(minutes).sendTo(messaging);
            TestEvent.forTrainer(second).minutes(100 + minutes).sendTo(messaging);
        }

        int firstTotal = sequence.stream().mapToInt(Integer::intValue).sum();
        int secondTotal = firstTotal + 100 * EVENTS_PER_TRAINER;
        Awaitility.await().ignoreExceptions().untilAsserted(() -> {
            Assertions.assertEquals(firstTotal, workloadService.getMonthlyDuration(first, 2026, 8));
            Assertions.assertEquals(secondTotal, workloadService.getMonthlyDuration(second, 2026, 8));
        });

        Assertions.assertEquals(sequence, appliedTo(applied, first).stream().map(Applied::minutes).toList());
        Assertions.assertEquals(sequence.stream().map(minutes -> 100 + minutes).toList(),
                appliedTo(applied, second).stream().map(Applied::minutes).toList());
        for (String username : List.of(first, second)) {
            Set<String> threads = appliedTo(applied, username).stream()
                    .map(Applied::thread)
                    .collect(Collectors.toSet());
            Assertions.assertEquals(1, threads.size(), username + " was handled by " + threads);
        }
    }
}
