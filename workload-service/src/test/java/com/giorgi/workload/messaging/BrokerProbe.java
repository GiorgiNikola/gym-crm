package com.giorgi.workload.messaging;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

public final class BrokerProbe {

    private final EmbeddedActiveMQ broker;

    public BrokerProbe(EmbeddedActiveMQ broker) {
        this.broker = broker;
    }

    public void purge(String queue) throws Exception {
        locate(queue).deleteAllReferences();
    }

    public long messageCount(String queue) {
        return locate(queue).getMessageCount();
    }

    private Queue locate(String queue) {
        return broker.getActiveMQServer().locateQueue(SimpleString.of(queue));
    }
}
