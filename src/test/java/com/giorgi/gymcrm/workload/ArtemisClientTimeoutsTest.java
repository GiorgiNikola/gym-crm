package com.giorgi.gymcrm.workload;

import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.CachingConnectionFactory;

import java.time.Duration;

class ArtemisClientTimeoutsTest {

    private static final WorkloadProperties.Messaging MESSAGING = new WorkloadProperties.Messaging("trainer.workload",
            Duration.ofMillis(1500), Duration.ofMillis(700), 3, Duration.ofMillis(200), 2, Duration.ofSeconds(3));

    private final ArtemisClientTimeouts timeouts = new ArtemisClientTimeouts(() -> MESSAGING);

    @Test
    @DisplayName("a tcp connection factory gets the call and connect timeouts")
    void boundsTheTcpClient() {
        ActiveMQConnectionFactory artemis = new ActiveMQConnectionFactory("tcp://localhost:61616");

        timeouts.postProcessAfterInitialization(new CachingConnectionFactory(artemis), "jmsConnectionFactory");

        Assertions.assertEquals(700, artemis.getCallTimeout());
        Assertions.assertEquals(700, artemis.getCallFailoverTimeout());
        Assertions.assertEquals(1500,
                artemis.getStaticConnectors()[0].getParams().get(TransportConstants.NETTY_CONNECT_TIMEOUT));
    }

    @Test
    @DisplayName("an in-vm connector only gets the call timeout")
    void leavesInVmTransportParamsAlone() {
        ActiveMQConnectionFactory artemis = new ActiveMQConnectionFactory("vm://0");

        timeouts.postProcessAfterInitialization(new CachingConnectionFactory(artemis), "jmsConnectionFactory");

        Assertions.assertEquals(700, artemis.getCallTimeout());
        Assertions.assertFalse(artemis.getStaticConnectors()[0].getParams()
                .containsKey(TransportConstants.NETTY_CONNECT_TIMEOUT));
    }

    @Test
    @DisplayName("other beans and other caching targets pass through untouched")
    void ignoresEverythingElse() {
        Object other = new Object();
        CachingConnectionFactory notArtemis = new CachingConnectionFactory();

        Assertions.assertSame(other, timeouts.postProcessAfterInitialization(other, "other"));
        Assertions.assertSame(notArtemis, timeouts.postProcessAfterInitialization(notArtemis, "caching"));
    }
}
