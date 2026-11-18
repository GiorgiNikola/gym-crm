package com.giorgi.workload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the spec is served")
    void servesSpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Workload service"));
    }

    @Test
    @DisplayName("the spec documents the three workload paths and nothing else")
    void documentsTheEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths.length()").value(3))
                .andExpect(jsonPath("$.paths['/workloads'].post").exists())
                .andExpect(jsonPath("$.paths['/workloads/{username}'].get").exists())
                .andExpect(jsonPath("$.paths['/workloads/{username}/{year}/{month}'].get").exists());
    }

    @Test
    @DisplayName("every endpoint documents its status codes")
    void documentsTheResponses() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/workloads'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/workloads'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/workloads/{username}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/workloads/{username}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/workloads/{username}/{year}/{month}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/workloads/{username}/{year}/{month}'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/workloads/{username}/{year}/{month}'].get.responses['404']").exists());
    }

    @Test
    @DisplayName("a success points at its own schema and an error at the problem detail")
    void documentsTheResponseBodies() throws Exception {
        String summary = "$.paths['/workloads/{username}'].get.responses";
        String month = "$.paths['/workloads/{username}/{year}/{month}'].get.responses";
        String problem = "['application/problem+json'].schema['$ref']";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(summary + "['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/TrainerWorkloadResponse"))
                .andExpect(jsonPath(month + "['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/MonthlyWorkloadResponse"))
                .andExpect(jsonPath("$.paths['/workloads'].post.responses['200'].content").doesNotExist())
                .andExpect(jsonPath("$.paths['/workloads'].post.responses['400'].content" + problem)
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(summary + "['404'].content" + problem)
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(month + "['400'].content" + problem)
                        .value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath(month + "['404'].content" + problem)
                        .value("#/components/schemas/ProblemDetail"));
    }

    @Test
    @DisplayName("the bearer token is declared as the security scheme")
    void declaresBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    @DisplayName("every endpoint points at the bearer scheme and documents 401 and 403")
    void marksEveryEndpointAsSecured() throws Exception {
        String post = "$.paths['/workloads'].post";
        String summary = "$.paths['/workloads/{username}'].get";
        String month = "$.paths['/workloads/{username}/{year}/{month}'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(post + ".security[0].bearerAuth").exists())
                .andExpect(jsonPath(summary + ".security[0].bearerAuth").exists())
                .andExpect(jsonPath(month + ".security[0].bearerAuth").exists())
                .andExpect(jsonPath(post + ".responses['401']").exists())
                .andExpect(jsonPath(post + ".responses['403']").exists())
                .andExpect(jsonPath(summary + ".responses['401']").exists())
                .andExpect(jsonPath(summary + ".responses['403']").exists())
                .andExpect(jsonPath(month + ".responses['401']").exists())
                .andExpect(jsonPath(month + ".responses['403']").exists());
    }

    @Test
    @DisplayName("the summary documents the trainer status as ACTIVE or INACTIVE")
    void documentsTheTrainerStatus() throws Exception {
        String status = "$.components.schemas.TrainerWorkloadResponse.properties.trainerStatus";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(status + ".enum[0]").value("ACTIVE"))
                .andExpect(jsonPath(status + ".enum[1]").value("INACTIVE"));
    }

    @Test
    @DisplayName("the request schema carries descriptions, examples and the required fields")
    void documentsTheRequestSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.WorkloadRequest.required.length()").value(7))
                .andExpect(jsonPath("$.components.schemas.WorkloadRequest.properties.isActive").exists())
                .andExpect(jsonPath("$.components.schemas.WorkloadRequest.properties.trainingDuration.description")
                        .value("How long the training lasts, in minutes"))
                .andExpect(jsonPath("$.components.schemas.WorkloadRequest.properties.trainingDate.example")
                        .value("2026-08-01"))
                .andExpect(jsonPath("$.components.schemas.WorkloadRequest.properties.actionType.enum.length()")
                        .value(2));
    }

    @Test
    @DisplayName("the swagger ui page is served")
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/swagger-ui.html leads to the swagger ui page")
    void redirectsToSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
