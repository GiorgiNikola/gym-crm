package com.giorgi.gymcrm.component;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockitoSpyBean(name = "workloadMessagingTemplate", types = JmsMessagingTemplate.class)
@Import({ScenarioState.class, GymCrmApi.class, WorkloadQueue.class, WorkloadServiceStub.class})
public class CucumberSpringConfiguration {

    // Eureka is off in tests; the simple discovery client hands the real load balancer the WireMock instance.
    @DynamicPropertySource
    static void workloadServiceInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.workload-service[0].uri",
                WorkloadServiceStub::baseUrl);
    }
}
