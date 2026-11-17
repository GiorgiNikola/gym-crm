package com.giorgi.workload.config;

import com.giorgi.workload.messaging.NonTextPayload;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.StreamMessage;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TextOnlyMessageConverterTest {

    private final TextOnlyMessageConverter converter = new TextOnlyMessageConverter();

    @Test
    @DisplayName("a text message becomes its text")
    void readsText() throws JMSException {
        TextMessage message = mock(TextMessage.class);
        when(message.getText()).thenReturn("{}");

        Assertions.assertEquals("{}", converter.fromMessage(message));
    }

    @Test
    @DisplayName("an object message is named, and its object is never touched")
    void namesObjectMessageWithoutReadingIt() throws JMSException {
        ObjectMessage message = mock(ObjectMessage.class);

        Assertions.assertEquals(new NonTextPayload("ObjectMessage"), converter.fromMessage(message));
        verifyNoInteractions(message);
    }

    @Test
    @DisplayName("map, stream and plain messages are named by their kind")
    void namesOtherKinds() throws JMSException {
        Assertions.assertEquals(new NonTextPayload("MapMessage"), converter.fromMessage(mock(MapMessage.class)));
        Assertions.assertEquals(new NonTextPayload("StreamMessage"),
                converter.fromMessage(mock(StreamMessage.class)));
        Assertions.assertEquals(new NonTextPayload("Message"), converter.fromMessage(mock(Message.class)));
    }
}
