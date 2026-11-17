package com.giorgi.workload.config;

import com.giorgi.workload.health.BrokerHealthIndicator;
import com.giorgi.workload.messaging.InMemoryProcessedEventRegistry;
import com.giorgi.workload.messaging.ProcessedEventRegistry;
import com.giorgi.workload.messaging.WorkloadMessagingProperties;
import jakarta.jms.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jms.ConnectionFactoryUnwrapper;
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.jms.core.JmsTemplate;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class MessagingConfig {

    public static final String LISTENER_CONTAINER_FACTORY = "jmsListenerContainerFactory";

    private static final int PROCESSED_EVENT_CAPACITY = 10_000;

    @Bean(LISTENER_CONTAINER_FACTORY)
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            DefaultJmsListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            WorkloadMessagingProperties properties) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, listenerConnectionFactory(connectionFactory));
        factory.setSessionTransacted(true);
        factory.setConcurrency(properties.concurrency());
        factory.setMessageConverter(new TextOnlyMessageConverter());
        factory.setErrorHandler(error -> log.debug("Listener failure, already logged as FAILED", error));
        return factory;
    }

    // Must be the listener's own connection factory: JmsTemplate joins the listener's transacted
    // session only when it looks up the session under the same factory instance.
    @Bean
    public JmsMessagingTemplate deadLetterTemplate(ConnectionFactory connectionFactory) {
        return new JmsMessagingTemplate(new JmsTemplate(listenerConnectionFactory(connectionFactory)));
    }

    @Bean
    public BrokerHealthIndicator brokerHealthIndicator(ConnectionFactory connectionFactory) {
        return new BrokerHealthIndicator(new JmsTemplate(listenerConnectionFactory(connectionFactory)));
    }

    @Bean
    public ProcessedEventRegistry processedEventRegistry() {
        return new InMemoryProcessedEventRegistry(PROCESSED_EVENT_CAPACITY);
    }

    private static ConnectionFactory listenerConnectionFactory(ConnectionFactory connectionFactory) {
        return ConnectionFactoryUnwrapper.unwrapCaching(connectionFactory);
    }
}
