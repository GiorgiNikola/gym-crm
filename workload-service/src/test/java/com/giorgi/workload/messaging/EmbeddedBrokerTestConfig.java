package com.giorgi.workload.messaging;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.artemis.autoconfigure.ArtemisConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.connection.CachingConnectionFactory;

@Profile("test")
@Configuration(proxyBeanMethods = false)
public class EmbeddedBrokerTestConfig {

    public static final String QUEUE = "trainer.workload";
    public static final String DEAD_LETTER_QUEUE = "DLQ.trainer.workload";
    public static final int MAX_DELIVERY_ATTEMPTS = 3;

    @Bean
    ArtemisConfigurationCustomizer workloadAddressSettings() {
        return configuration -> {
            configuration.addAddressSetting(QUEUE, anycast()
                    .setMaxDeliveryAttempts(MAX_DELIVERY_ATTEMPTS)
                    .setRedeliveryDelay(500)
                    .setRedeliveryMultiplier(2)
                    .setMaxRedeliveryDelay(5000)
                    .setDeadLetterAddress(SimpleString.of(DEAD_LETTER_QUEUE)));
            configuration.addAddressSetting(DEAD_LETTER_QUEUE, anycast());
            configuration.addQueueConfiguration(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));
            configuration.addQueueConfiguration(QueueConfiguration.of(DEAD_LETTER_QUEUE)
                    .setRoutingType(RoutingType.ANYCAST));
        };
    }

    @Bean
    static BeanPostProcessor withoutConsumerPrefetch() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof CachingConnectionFactory caching
                        && caching.getTargetConnectionFactory() instanceof ActiveMQConnectionFactory artemis) {
                    artemis.setConsumerWindowSize(0);
                }
                return bean;
            }
        };
    }

    private static AddressSettings anycast() {
        return new AddressSettings()
                .setAutoCreateAddresses(true)
                .setAutoCreateQueues(true)
                .setDefaultAddressRoutingType(RoutingType.ANYCAST)
                .setDefaultQueueRoutingType(RoutingType.ANYCAST);
    }
}
