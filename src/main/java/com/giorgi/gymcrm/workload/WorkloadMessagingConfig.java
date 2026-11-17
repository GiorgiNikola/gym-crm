package com.giorgi.gymcrm.workload;

import com.giorgi.gymcrm.health.BrokerHealthIndicator;
import jakarta.jms.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageType;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class WorkloadMessagingConfig {

    static JacksonJsonMessageConverter workloadMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName(WorkloadMessageHeaders.TYPE_ID);
        converter.setTypeIdMappings(Map.of(WorkloadMessageHeaders.TYPE, WorkloadMessage.class));
        return converter;
    }

    @Bean
    public JmsMessagingTemplate workloadMessagingTemplate(ConnectionFactory connectionFactory, JsonMapper jsonMapper) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setMessageConverter(workloadMessageConverter(jsonMapper));
        jmsTemplate.setExplicitQosEnabled(true);
        jmsTemplate.setDeliveryPersistent(true);
        return new JmsMessagingTemplate(jmsTemplate);
    }

    @Bean
    public BrokerHealthIndicator brokerHealthIndicator(ConnectionFactory connectionFactory) {
        return new BrokerHealthIndicator(new JmsTemplate(connectionFactory));
    }

    @Bean
    static BeanPostProcessor artemisClientTimeouts(ObjectProvider<WorkloadProperties> properties) {
        return new ArtemisClientTimeouts(() -> properties.getObject().messaging());
    }
}
