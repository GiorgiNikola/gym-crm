package com.giorgi.integration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.HealthState;
import com.github.dockerjava.api.command.HealthStateLog;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class GymCrmSystem {

    private static final Logger log = LoggerFactory.getLogger(GymCrmSystem.class);

    public static final String GYM_CRM = "app";
    public static final String WORKLOAD_SERVICE = "workload-service";
    public static final String BROKER = "artemis";

    private static final List<String> SERVICES =
            List.of("db", "artemis", "mongo", "discovery-server", WORKLOAD_SERVICE, GYM_CRM);
    private static final String PROJECT = "gymcrm-it";
    private static final Duration STARTUP = Duration.ofMinutes(10);
    private static final Duration HEALTHY_AGAIN = Duration.ofMinutes(3);

    private static GymCrmSystem running;

    private final ComposeContainer compose;
    private final DockerClient docker;
    private final Map<String, URI> addresses = new ConcurrentHashMap<>();

    private GymCrmSystem(ComposeContainer compose, DockerClient docker) {
        this.compose = compose;
        this.docker = docker;
    }

    public static synchronized void start() {
        if (running != null) {
            return;
        }
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException("Docker is not running. The integration suite starts the whole system "
                    + "with docker compose: start Docker and run ./mvnw -f integration-tests/pom.xml verify again.");
        }
        ComposeContainer compose = new ComposeContainer(PROJECT, renderedComposeFile())
                .withBuild(true)
                .withRemoveVolumes(true)
                .withRemoveImages(ComposeContainer.RemoveImages.LOCAL)
                .withStartupTimeout(STARTUP);
        for (String service : SERVICES) {
            compose.waitingFor(service, Wait.forHealthcheck().withStartupTimeout(STARTUP));
            compose.withLogConsumer(service, new ContainerLogFile(logFile(service)));
        }
        try {
            compose.start();
        } catch (RuntimeException failure) {
            compose.stop();
            throw failure;
        }
        running = new GymCrmSystem(compose, DockerClientFactory.instance().client());
    }

    public static synchronized void stopAll() {
        if (running != null) {
            running.compose.stop();
            running = null;
        }
    }

    public static GymCrmSystem get() {
        if (running == null) {
            throw new IllegalStateException("the system is not running; see the first scenario's failure");
        }
        return running;
    }

    public URI address(String service, int containerPort) {
        return addresses.computeIfAbsent(service + ":" + containerPort, key -> {
            Ports.Binding[] bindings = inspect(service).getNetworkSettings().getPorts().getBindings()
                    .get(ExposedPort.tcp(containerPort));
            if (bindings == null || bindings.length == 0) {
                throw new IllegalStateException(service + " does not publish port " + containerPort);
            }
            return URI.create("http://127.0.0.1:" + bindings[0].getHostPortSpec());
        });
    }

    public Map<String, String> environment(String service) {
        return Arrays.stream(inspect(service).getConfig().getEnv())
                .map(variable -> variable.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair.length > 1 ? pair[1] : ""));
    }

    public void stop(String service) {
        log.info("Stopping {}", service);
        docker.stopContainerCmd(containerId(service)).exec();
    }

    public void startAgain(String service) {
        addresses.keySet().removeIf(key -> key.startsWith(service + ":"));
        InspectContainerResponse.ContainerState before = inspect(service).getState();
        log.info("Starting {} again: running={} status={}", service, before.getRunning(), before.getStatus());
        if (!Boolean.TRUE.equals(before.getRunning())) {
            docker.startContainerCmd(containerId(service)).exec();
        }
        long startedAt = System.nanoTime();
        Awaitility.await("%s healthy again".formatted(service)).atMost(HEALTHY_AGAIN).until(() -> isHealthy(service));
        log.info("{} healthy again after {} ms", service, (System.nanoTime() - startedAt) / 1_000_000);
    }

    // After a restart Docker keeps reporting the previous run's "healthy" while checks fail in the start
    // period, so only a passing check that ran after this start counts.
    private boolean isHealthy(String service) {
        InspectContainerResponse.ContainerState state = inspect(service).getState();
        HealthState health = state.getHealth();
        if (health == null || health.getLog() == null || health.getLog().isEmpty()) {
            return false;
        }
        HealthStateLog lastCheck = health.getLog().getLast();
        return "healthy".equals(health.getStatus())
                && Long.valueOf(0).equals(lastCheck.getExitCodeLong())
                && OffsetDateTime.parse(lastCheck.getStart()).isAfter(OffsetDateTime.parse(state.getStartedAt()));
    }

    private InspectContainerResponse inspect(String service) {
        return docker.inspectContainerCmd(containerId(service)).exec();
    }

    private String containerId(String service) {
        return compose.getContainerByServiceName(service)
                .map(ContainerState::getContainerId)
                .orElseThrow(() -> new IllegalStateException("no container for service " + service));
    }

    // Testcontainers reads the files itself and refuses container_name, so it gets the merged model compose
    // renders from compose.yml plus the override, where those names are already reset.
    private static File renderedComposeFile() {
        File rendered = composeFile("integration.compose.rendered");
        List<String> command = List.of("docker", "compose", "--project-name", PROJECT,
                "-f", composeFile("integration.compose.file").getPath(),
                "-f", composeFile("integration.compose.override").getPath(),
                "config", "--output", rendered.getPath());
        try {
            rendered.getParentFile().mkdirs();
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("docker compose could not merge the compose files: " + output);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("docker compose is not available on the PATH", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return rendered;
    }

    private static Path logFile(String service) {
        return composeFile("integration.logs.dir").toPath().resolve(service + ".log");
    }

    private static File composeFile(String property) {
        String path = System.getProperty(property);
        if (path == null) {
            throw new IllegalStateException(property + " is not set; run the suite through "
                    + "./mvnw -f integration-tests/pom.xml verify");
        }
        return new File(path).getAbsoluteFile();
    }
}
