package com.giorgi.workload.messaging;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.reader.TextMessageUtil;

import java.time.Instant;
import java.util.Map;

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

    public int retryAll(String deadLetterQueue) throws Exception {
        QueueControl control = (QueueControl) broker.getActiveMQServer().getManagementService()
                .getResource(ResourceNames.QUEUE + deadLetterQueue);
        return control.retryMessages();
    }

    // The JMS producer always stamps the current time, so a message "sent" in the past goes through the core API.
    public void send(String queue, String text, Map<String, Object> properties, Instant sentAt) throws Exception {
        TransportConfiguration acceptor = broker.getActiveMQServer().getConfiguration()
                .getAcceptorConfigurations().iterator().next();
        try (ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(
                new TransportConfiguration(InVMConnectorFactory.class.getName(), acceptor.getParams()));
             ClientSessionFactory factory = locator.createSessionFactory();
             ClientSession session = factory.createSession();
             ClientProducer producer = session.createProducer(queue)) {
            ClientMessage message = session.createMessage(Message.TEXT_TYPE, true);
            TextMessageUtil.writeBodyText(message.getBodyBuffer(), SimpleString.of(text));
            properties.forEach((name, value) -> message.putObjectProperty(
                    WorkloadEventHeaders.GROUP_ID.equals(name) ? Message.HDR_GROUP_ID.toString() : name, value));
            message.setTimestamp(sentAt.toEpochMilli());
            producer.send(message);
        }
    }

    private Queue locate(String queue) {
        return broker.getActiveMQServer().locateQueue(SimpleString.of(queue));
    }
}
