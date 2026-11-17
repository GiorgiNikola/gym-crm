package com.giorgi.workload.config;

import com.giorgi.workload.messaging.NonTextPayload;
import jakarta.jms.BytesMessage;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;
import org.springframework.jms.support.converter.SimpleMessageConverter;

import java.util.stream.Stream;

// SimpleMessageConverter calls ObjectMessage.getObject(), which deserializes whatever the sender put in.
class TextOnlyMessageConverter extends SimpleMessageConverter {

    @Override
    public Object fromMessage(Message message) throws JMSException {
        if (message instanceof TextMessage text) {
            return text.getText();
        }
        return new NonTextPayload(messageType(message));
    }

    private static String messageType(Message message) {
        return Stream.of(BytesMessage.class, MapMessage.class, ObjectMessage.class, StreamMessage.class)
                .filter(type -> type.isInstance(message))
                .map(Class::getSimpleName)
                .findFirst()
                .orElse(Message.class.getSimpleName());
    }
}
