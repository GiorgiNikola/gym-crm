package com.giorgi.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class GymCrmClient {

    private static final String TRANSACTION_ID_HEADER = "X-Transaction-Id";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    public ApiResponse post(String path, Map<String, ?> body, String token) {
        return send(request(path, token).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .header("Content-Type", "application/json"));
    }

    public ApiResponse get(String path, String token) {
        return send(request(path, token).GET());
    }

    public ApiResponse delete(String path, String token) {
        return send(request(path, token).DELETE());
    }

    public JsonNode json(ApiResponse response) {
        return json.readTree(response.body());
    }

    private HttpRequest.Builder request(String path, String token) {
        URI base = GymCrmSystem.get().address(GymCrmSystem.GYM_CRM, 8080);
        HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header(TRANSACTION_ID_HEADER, "it-" + UUID.randomUUID());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private ApiResponse send(HttpRequest.Builder builder) {
        HttpRequest request = builder.build();
        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.body(),
                    request.headers().firstValue(TRANSACTION_ID_HEADER).orElseThrow(),
                    Duration.ofNanos(System.nanoTime() - startedAt));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
