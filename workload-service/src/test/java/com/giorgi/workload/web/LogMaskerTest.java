package com.giorgi.workload.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class LogMaskerTest {

    @Test
    @DisplayName("the first and the last name are masked and the username is kept")
    void masksNames() {
        String masked = LogMasker.mask("{\"trainerUsername\":\"Sarah.Miller\",\"trainerFirstName\":\"Sarah\","
                + "\"trainerLastName\" : \"Miller\",\"trainingDuration\":60}");

        Assertions.assertEquals("{\"trainerUsername\":\"Sarah.Miller\",\"trainerFirstName\":\"****\","
                + "\"trainerLastName\" : \"****\",\"trainingDuration\":60}", masked);
    }

    @Test
    @DisplayName("a password or a token is masked whatever its prefix or case")
    void masksSecrets() {
        String masked = LogMasker.mask("{\"password\":\"secret123\",\"accessToken\":\"eyJhbGciOi\","
                + "\"NEWPASSWORD\":\"x\"}");

        Assertions.assertEquals("{\"password\":\"****\",\"accessToken\":\"****\",\"NEWPASSWORD\":\"****\"}",
                masked);
    }

    @Test
    @DisplayName("a masked value may contain an escaped quote")
    void masksValuesWithEscapedQuotes() {
        String masked = LogMasker.mask("{\"trainerLastName\":\"O\\\"Neil\",\"actionType\":\"ADD\"}");

        Assertions.assertEquals("{\"trainerLastName\":\"****\",\"actionType\":\"ADD\"}", masked);
    }

    @Test
    @DisplayName("a body without masked fields is left as it is")
    void leavesOtherBodiesAlone() {
        String body = "{\"detail\":\"Trainer not found: Sarah.Miller\",\"status\":404}";

        Assertions.assertEquals(body, LogMasker.mask(body));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("nothing to mask comes back unchanged")
    void returnsEmptyInputUnchanged(String body) {
        Assertions.assertEquals(body, LogMasker.mask(body));
    }
}
