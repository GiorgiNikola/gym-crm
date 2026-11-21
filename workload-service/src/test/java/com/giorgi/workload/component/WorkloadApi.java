package com.giorgi.workload.component;

import com.giorgi.workload.TestTokens;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

public class WorkloadApi {

    private final Environment environment;
    private final JsonMapper jsonMapper;

    public WorkloadApi(Environment environment, JsonMapper jsonMapper) {
        this.environment = environment;
        this.jsonMapper = jsonMapper;
    }

    public ApiResponse record(ScenarioState trainer, LocalDate trainingDate, int minutes) {
        String body = """
                {"trainerUsername":"%s","trainerFirstName":"%s","trainerLastName":"%s","isActive":true,
                 "trainingDate":"%s","trainingDuration":%d,"actionType":"ADD"}""".formatted(
                trainer.username(), trainer.firstName(), trainer.lastName(), trainingDate, minutes);
        return client().post().uri("/workloads")
                .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(),
                        response.getHeaders().getContentType(), response.bodyTo(String.class)));
    }

    public ApiResponse monthly(String username, int year, int month, String authorization) {
        return get(authorization, "/workloads/{username}/{year}/{month}", username, year, month);
    }

    public ApiResponse summary(String username, String authorization) {
        return get(authorization, "/workloads/{username}", username);
    }

    public <T> T read(ApiResponse response, Class<T> type) {
        return jsonMapper.readValue(response.body(), type);
    }

    public JsonNode tree(ApiResponse response) {
        return jsonMapper.readTree(response.body());
    }

    private ApiResponse get(String authorization, String path, Object... variables) {
        return client().get().uri(path, variables)
                .headers(headers -> {
                    if (authorization != null) {
                        headers.set(HttpHeaders.AUTHORIZATION, authorization);
                    }
                })
                .exchange((request, response) -> new ApiResponse(response.getStatusCode().value(),
                        response.getHeaders().getContentType(), response.bodyTo(String.class)));
    }

    private RestClient client() {
        return RestClient.create("http://localhost:" + environment.getRequiredProperty("local.server.port"));
    }
}
