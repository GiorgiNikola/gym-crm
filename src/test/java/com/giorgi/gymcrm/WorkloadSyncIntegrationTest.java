package com.giorgi.gymcrm;

import com.giorgi.gymcrm.dto.RegistrationResponse;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.service.TrainingService;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import com.giorgi.gymcrm.workload.ActionType;
import com.giorgi.gymcrm.workload.WorkloadClient;
import com.giorgi.gymcrm.workload.WorkloadRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.ConnectException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkloadSyncIntegrationTest {

    private static final LocalDate AUGUST_FIRST = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEPTEMBER_TENTH = LocalDate.of(2026, 9, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TrainingService trainingService;

    @Autowired
    private TraineeService traineeService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private WorkloadClient workloadClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void closeTheBreaker() {
        circuitBreakerRegistry.circuitBreaker("workload").reset();
    }

    @AfterEach
    void clearRegisteredRows() {
        JdbcTestUtils.deleteFromTables(new JdbcTemplate(dataSource),
                "trainee2trainer", "trainings", "trainees", "trainers", "users");
    }

    private RegistrationResponse register(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, RegistrationResponse.class);
    }

    private RegistrationResponse registerTrainee() throws Exception {
        return register("/trainees", "{\"firstName\":\"John\",\"lastName\":\"Smith\"}");
    }

    private RegistrationResponse registerTrainer() throws Exception {
        return register("/trainers", "{\"firstName\":\"Sarah\",\"lastName\":\"Miller\",\"specialization\":\"YOGA\"}");
    }

    private WorkloadRequest change(String trainer, LocalDate date, int minutes, ActionType action) {
        return new WorkloadRequest(trainer, "Sarah", "Miller", true, date, minutes, action);
    }

    private String trainingBody(String trainee, String trainer) {
        return "{\"traineeUsername\":\"" + trainee + "\",\"trainerUsername\":\"" + trainer + "\","
                + "\"trainingName\":\"Morning flow\",\"trainingDate\":\"2026-08-01\",\"trainingDuration\":60}";
    }

    @Test
    @DisplayName("adding a training sends one ADD with the trainer and the training once it is committed")
    void sendsAddAfterCommit() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();

        trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);

        verify(workloadClient).recordChange(change(trainer.username(), AUGUST_FIRST, 60, ActionType.ADD));
    }

    @Test
    @DisplayName("POST /trainings forwards its transaction id to workload-service on the request thread")
    void forwardsTheTransactionId() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        AtomicReference<String> transactionIdDuringCall = new AtomicReference<>();
        AtomicReference<Thread> threadDuringCall = new AtomicReference<>();
        doAnswer(invocation -> {
            transactionIdDuringCall.set(MDC.get(TransactionIdFilter.MDC_KEY));
            threadDuringCall.set(Thread.currentThread());
            return null;
        }).when(workloadClient).recordChange(any());

        mockMvc.perform(post("/trainings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + trainee.token())
                        .header(TransactionIdFilter.TRANSACTION_ID_HEADER, "demo-add-training-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingBody(trainee.username(), trainer.username())))
                .andExpect(status().isOk());

        Assertions.assertEquals("demo-add-training-0001", transactionIdDuringCall.get());
        Assertions.assertSame(Thread.currentThread(), threadDuringCall.get());
    }

    @Test
    @DisplayName("POST /trainings still succeeds while workload-service cannot be reached")
    void addSucceedsWhileWorkloadIsDown() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        doThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")))
                .when(workloadClient).recordChange(any());

        mockMvc.perform(post("/trainings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + trainee.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trainingBody(trainee.username(), trainer.username())))
                .andExpect(status().isOk());

        Assertions.assertEquals(1, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainings"));
    }

    @Test
    @DisplayName("a training whose transaction rolls back is never sent")
    void sendsNothingOnRollback() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();

        transactionTemplate.executeWithoutResult(status -> {
            trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);
            status.setRollbackOnly();
        });

        verifyNoInteractions(workloadClient);
        Assertions.assertEquals(0, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainings"));
    }

    @Test
    @DisplayName("deleting a trainee sends one DELETE per training, with the payload of that training")
    void traineeDeleteSendsOneDeletePerTraining() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);
        trainingService.addTraining(trainee.username(), trainer.username(), "Evening flow", SEPTEMBER_TENTH, 45L);
        clearInvocations(workloadClient);

        mockMvc.perform(delete("/trainees/" + trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + trainee.token()))
                .andExpect(status().isOk());

        ArgumentCaptor<WorkloadRequest> sent = ArgumentCaptor.forClass(WorkloadRequest.class);
        verify(workloadClient, times(2)).recordChange(sent.capture());
        Assertions.assertEquals(List.of(
                        change(trainer.username(), AUGUST_FIRST, 60, ActionType.DELETE),
                        change(trainer.username(), SEPTEMBER_TENTH, 45, ActionType.DELETE)),
                sent.getAllValues());
        Assertions.assertEquals(0, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainings"));
    }

    @Test
    @DisplayName("deleting a trainee without trainings sends nothing")
    void traineeDeleteWithoutTrainingsSendsNothing() throws Exception {
        RegistrationResponse trainee = registerTrainee();

        traineeService.deleteByUsername(trainee.username());

        verifyNoInteractions(workloadClient);
    }

    @Test
    @DisplayName("a trainee delete that rolls back sends nothing")
    void traineeDeleteRollbackSendsNothing() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);
        clearInvocations(workloadClient);

        transactionTemplate.executeWithoutResult(status -> {
            traineeService.deleteByUsername(trainee.username());
            status.setRollbackOnly();
        });

        verifyNoInteractions(workloadClient);
        Assertions.assertEquals(1, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainings"));
    }

    @Test
    @DisplayName("a trainee delete still succeeds while workload-service cannot be reached")
    void traineeDeleteSucceedsWhileWorkloadIsDown() throws Exception {
        RegistrationResponse trainee = registerTrainee();
        RegistrationResponse trainer = registerTrainer();
        trainingService.addTraining(trainee.username(), trainer.username(), "Morning flow", AUGUST_FIRST, 60L);
        doThrow(new ResourceAccessException("I/O error", new ConnectException("Connection refused")))
                .when(workloadClient).recordChange(any());

        mockMvc.perform(delete("/trainees/" + trainee.username())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + trainee.token()))
                .andExpect(status().isOk());

        Assertions.assertEquals(0, JdbcTestUtils.countRowsInTable(new JdbcTemplate(dataSource), "trainees"));
    }
}
