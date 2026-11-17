package com.giorgi.gymcrm.workload;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jms.connection.CachingConnectionFactory;

import java.util.function.Supplier;

class ArtemisClientTimeouts implements BeanPostProcessor {

    private final Supplier<WorkloadProperties.Messaging> messaging;

    ArtemisClientTimeouts(Supplier<WorkloadProperties.Messaging> messaging) {
        this.messaging = messaging;
    }

    // The Artemis defaults (30 s per blocking call, the 30 s Netty connect timeout) would hold the request thread
    // far longer than the publish retries are meant to.
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof CachingConnectionFactory caching
                && caching.getTargetConnectionFactory() instanceof ActiveMQConnectionFactory artemis) {
            apply(artemis, messaging.get());
        }
        return bean;
    }

    static void apply(ActiveMQConnectionFactory artemis, WorkloadProperties.Messaging messaging) {
        long callTimeout = messaging.callTimeout().toMillis();
        artemis.setCallTimeout(callTimeout);
        artemis.setCallFailoverTimeout(callTimeout);
        for (TransportConfiguration connector : artemis.getStaticConnectors()) {
            if (NettyConnectorFactory.class.getName().equals(connector.getFactoryClassName())) {
                connector.getParams().put(TransportConstants.NETTY_CONNECT_TIMEOUT,
                        Math.toIntExact(messaging.connectTimeout().toMillis()));
            }
        }
    }
}
