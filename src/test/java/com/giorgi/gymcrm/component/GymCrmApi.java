package com.giorgi.gymcrm.component;

import com.giorgi.gymcrm.web.TransactionIdFilter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

public class GymCrmApi {

    private final Environment environment;
    private final JsonMapper jsonMapper;

    public GymCrmApi(Environment environment, JsonMapper jsonMapper) {
        this.environment = environment;
        this.jsonMapper = jsonMapper;
    }

    public ApiResponse post(String path, Map<String, ?> body, String token) {
        return call(HttpMethod.POST, path, jsonMapper.writeValueAsString(body), token);
    }

    public ApiResponse get(String path, String token) {
        return call(HttpMethod.GET, path, null, token);
    }

    public ApiResponse delete(String path, String token) {
        return call(HttpMethod.DELETE, path, null, token);
    }

    public <T> T read(ApiResponse response, Class<T> type) {
        return jsonMapper.readValue(response.body(), type);
    }

    public JsonNode tree(String json) {
        return jsonMapper.readTree(json);
    }

    private ApiResponse call(HttpMethod method, String path, String body, String token) {
        String transactionId = "component-" + UUID.randomUUID();
        RestClient.RequestBodySpec request = client().method(method).uri(path)
                .header(TransactionIdFilter.TRANSACTION_ID_HEADER, transactionId);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return request.exchange((sent, response) -> new ApiResponse(response.getStatusCode().value(),
                response.getHeaders().getContentType(), response.bodyTo(String.class), transactionId));
    }

    // The JDK client never retries; Apache's default would wait out a 429's Retry-After and resend.
    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + environment.getRequiredProperty("local.server.port"))
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
    }
}
