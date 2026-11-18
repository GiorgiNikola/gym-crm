// Shows the broker's own redelivery: sends one message, rolls back three deliveries, then the broker moves it
// to DLQ.trainer.workload. Stop workload-service first so it does not take the message. The artemis image
// ships no JDK, so compile on the host against its client jars (javax.jms) and run it inside the container:
// D=$(mktemp -d) && docker cp -q artemis:/opt/activemq-artemis/lib "$D/lib" && javac -proc:none -cp "$D/lib/*" -d "$D" scripts/messaging/Rollback.java && docker cp -q "$D/Rollback.class" artemis:/tmp/ && docker exec artemis java -cp "/tmp:/opt/activemq-artemis/lib/*" Rollback
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

public class Rollback {
    public static void main(String[] args) throws Exception {
        var factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        try (Connection connection = factory.createConnection(System.getenv("ARTEMIS_USER"), System.getenv("ARTEMIS_PASSWORD"))) {
            connection.start();
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            var queue = session.createQueue("trainer.workload");
            Message message = session.createTextMessage("{}");
            message.setStringProperty("authorization", "MARKER-fake-token");
            message.setStringProperty("transactionId", "demo-amq222149");
            session.createProducer(queue).send(message);
            session.commit();
            MessageConsumer consumer = session.createConsumer(queue);
            for (int i = 1; i <= 3; i++) {
                Message received = consumer.receive(8000);
                System.out.println("attempt " + i + ": " + (received == null ? "none" : "delivery " + received.getIntProperty("JMSXDeliveryCount")));
                session.rollback();
            }
            System.out.println("after 3 rollbacks: " + (consumer.receive(8000) == null ? "queue empty" : "still there"));
        }
    }
}
