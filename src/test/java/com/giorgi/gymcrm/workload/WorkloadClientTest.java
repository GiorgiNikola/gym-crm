package com.giorgi.gymcrm.workload;

import ch.qos.logback.classic.Level;
import com.giorgi.gymcrm.AdjustableClock;
import com.giorgi.gymcrm.LogCapture;
import com.giorgi.gymcrm.exception.WorkloadUnavailableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class WorkloadClientTest {

    private static final String SECRET = "test-only-workload-secret-0123456789abcdef";

    private final Map<String, String> received = new ConcurrentHashMap<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final ExecutorService handlers = Executors.newCachedThreadPool();

    private HttpServer server;
    private WorkloadProperties properties;
    private ServiceTokenProvider tokens;
    private WorkloadClient client;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(handlers);
        server.createContext("/workloads", this::handle);
        server.start();

        properties = new WorkloadProperties("http://localhost:" + server.getAddress().getPort(), SECRET,
                Duration.ofSeconds(2), Duration.ofSeconds(3), null);
        tokens = new ServiceTokenProvider(properties, new AdjustableClock(Instant.now()));
        client = WorkloadClientConfig.createClient(WorkloadClientConfig.restClientBuilder(properties), tokens);
    }

    @AfterEach
    void stopStub() {
        MDC.clear();
        release.countDown();
        server.stop(0);
        handlers.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        received.put("method", exchange.getRequestMethod());
        received.put("path", exchange.getRequestURI().getPath());
        received.put("authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        received.put("userAgent", String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));

        if (exchange.getRequestURI().getPath().startsWith("/workloads/Slow.Trainer/")) {
            awaitRelease();
        }
        byte[] body = "{\"trainerUsername\":\"Sarah.Miller\",\"year\":2026,\"month\":8,\"trainingSummaryDuration\":150}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void awaitRelease() {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("GET /workloads/{username}/{year}/{month} is read into the monthly summary")
    void readsTheMonth() {
        MonthlyWorkloadResponse month = client.getMonthlyWorkload("Sarah.Miller", 2026, 8);

        Assertions.assertEquals(new MonthlyWorkloadResponse("Sarah.Miller", 2026, 8, 150), month);
        Assertions.assertEquals("GET", received.get("method"));
        Assertions.assertEquals("/workloads/Sarah.Miller/2026/8", received.get("path"));
        Assertions.assertEquals("Bearer " + tokens.readToken(), received.get("authorization"));
    }

    @Test
    @DisplayName("the JDK HTTP client sends the call, although Apache HttpClient is on the classpath")
    void usesTheJdkClient() {
        Assertions.assertTrue(ClassUtils.isPresent("org.apache.hc.client5.http.impl.classic.HttpClients", null));

        client.getMonthlyWorkload("Sarah.Miller", 2026, 8);

        Assertions.assertTrue(received.get("userAgent").startsWith("Java-http-client/"), received.get("userAgent"));
    }

    @Test
    @DisplayName("a response slower than the 3 second read timeout ends in the fallback, not a hang")
    void appliesTheReadTimeout() {
        WorkloadGateway gateway = new WorkloadGateway(client, Breakers.onCallerThread(CircuitBreakerRegistry.ofDefaults()));
        long startedAt = System.nanoTime();

        try (LogCapture logs = LogCapture.of(WorkloadGateway.class)) {
            Assertions.assertThrows(WorkloadUnavailableException.class,
                    () -> gateway.monthlyMinutes("Slow.Trainer", 2026, 8));

            Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);
            Assertions.assertEquals(Duration.ofSeconds(3), properties.readTimeout());
            Assertions.assertTrue(waited.compareTo(Duration.ofSeconds(3)) >= 0, waited.toString());
            Assertions.assertTrue(waited.compareTo(Duration.ofSeconds(6)) < 0, waited.toString());
            Assertions.assertEquals(List.of("Workload not read: transactionId=null trainer=Slow.Trainer "
                    + "month=2026-08 reason=HttpTimeoutException"), logs.messages(Level.WARN));
        }
    }
}
