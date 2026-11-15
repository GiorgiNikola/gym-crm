package com.giorgi.gymcrm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the spec is served without credentials")
    void servesSpecToAnyone() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }

    @Test
    @DisplayName("the spec documents the registration and the training type paths")
    void documentsTheEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/trainees'].post").exists())
                .andExpect(jsonPath("$.paths['/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/trainees/{username}'].get").exists())
                .andExpect(jsonPath("$.paths['/training-types'].get").exists())
                .andExpect(jsonPath("$.paths['/trainings'].post").exists());
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
    @DisplayName("the old basic auth scheme is gone")
    void dropsTheBasicScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth").doesNotExist());
    }

    @Test
    @DisplayName("a secured endpoint points at that scheme and a public one does not")
    void marksSecuredEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/training-types'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/trainees/{username}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/trainees'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/auth/login'].post.security").doesNotExist());
    }

    @Test
    @DisplayName("a path with a username documents that one parameter and nothing else")
    void documentsOnlyThePathParameter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/trainees/{username}'].get.parameters.length()").value(1))
                .andExpect(jsonPath("$.paths['/trainees/{username}'].get.parameters[0].name").value("username"));
    }

    @Test
    @DisplayName("the swagger ui page is served")
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
