package com.giorgi.gymcrm;

import com.giorgi.gymcrm.config.RootConfig;
import com.giorgi.gymcrm.config.WebConfig;
import com.giorgi.gymcrm.web.RequestLoggingFilter;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextHierarchy({
        @ContextConfiguration(classes = RootConfig.class),
        @ContextConfiguration(classes = WebConfig.class)
})
class WebContextTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(new TransactionIdFilter(), new RequestLoggingFilter())
                .build();
    }

    @Test
    @DisplayName("GET /training-types goes through the real wiring to the database")
    void servesTrainingTypes() throws Exception {
        mockMvc.perform(get("/training-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("every response carries a transaction id")
    void tagsResponsesWithATransactionId() throws Exception {
        mockMvc.perform(get("/training-types"))
                .andExpect(header().exists(TransactionIdFilter.TRANSACTION_ID_HEADER));
    }

    @Test
    @DisplayName("an unknown path comes back as a problem detail")
    void reportsUnknownPathAsProblem() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
