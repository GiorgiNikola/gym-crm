package com.giorgi.gymcrm.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMaskerTest {

    @Test
    @DisplayName("every field whose name ends with password is masked")
    void masksPasswordFields() {
        String body = """
                {"username":"John.Smith","password":"secret123","oldPassword":"old1","newPassword":"new1"}""";

        String masked = LogMasker.mask(body);

        Assertions.assertFalse(masked.contains("secret123"), masked);
        Assertions.assertFalse(masked.contains("old1"), masked);
        Assertions.assertFalse(masked.contains("new1"), masked);
        Assertions.assertTrue(masked.contains("\"password\":\"****\""), masked);
        Assertions.assertTrue(masked.contains("\"oldPassword\":\"****\""), masked);
        Assertions.assertTrue(masked.contains("\"newPassword\":\"****\""), masked);
    }

    @Test
    @DisplayName("a password with an escaped quote is masked completely")
    void masksEscapedQuoteInPassword() {
        String body = """
                {"password":"abc\\"def","username":"John.Smith"}""";

        String masked = LogMasker.mask(body);

        Assertions.assertFalse(masked.contains("def"), masked);
        Assertions.assertTrue(masked.contains("\"username\":\"John.Smith\""), masked);
    }

    @Test
    @DisplayName("other fields stay readable")
    void leavesOtherFieldsAlone() {
        String body = """
                {"username":"John.Smith","address":"123 Main St New York"}""";

        Assertions.assertEquals(body, LogMasker.mask(body));
    }

    @Test
    @DisplayName("null and empty bodies come back unchanged")
    void passesThroughNullAndEmpty() {
        Assertions.assertNull(LogMasker.mask(null));
        Assertions.assertEquals("", LogMasker.mask(""));
    }
}
