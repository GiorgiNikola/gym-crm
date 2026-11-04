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

// Read only: the docs are generated from the annotations, so nothing is written and there is
// nothing to clean up afterwards.
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
                .andExpect(jsonPath("$.paths['/trainees/{username}'].get").exists())
                .andExpect(jsonPath("$.paths['/training-types'].get").exists())
                .andExpect(jsonPath("$.paths['/trainings'].post").exists());
    }

    @Test
    @DisplayName("basic auth is declared as a security scheme")
    void declaresBasicAuthScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"));
    }

    @Test
    @DisplayName("a secured endpoint points at that scheme and a public one does not")
    void marksSecuredEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/training-types'].get.security[0].basicAuth").exists())
                .andExpect(jsonPath("$.paths['/trainees/{username}'].get.security[0].basicAuth").exists())
                .andExpect(jsonPath("$.paths['/trainees'].post.security").doesNotExist());
    }

    @Test
    @DisplayName("the hidden credentials parameter stays out of the spec")
    void hidesTheCredentialsParameter() throws Exception {
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
