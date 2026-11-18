package com.giorgi.workload.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

class WorkloadSecurityPropertiesTest {

    private static final String THIRTY_TWO_BYTES = "0123456789abcdef0123456789abcdef";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "0123456789abcdef0123456789abcde"})
    @DisplayName("a missing secret or one under 32 bytes is rejected with a message that names the variable")
    void rejectsShortSecret(String secret) {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new WorkloadSecurityProperties(secret));

        Assertions.assertEquals("workload.security.jwt-secret must be at least 32 bytes: set the "
                + "WORKLOAD_JWT_SECRET environment variable to a random value of that length", thrown.getMessage());
    }

    @Test
    @DisplayName("the rejection never repeats the secret")
    void keepsSecretOutOfTheMessage() {
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> new WorkloadSecurityProperties("hunter2"));

        Assertions.assertFalse(thrown.getMessage().contains("hunter2"));
    }

    @Test
    @DisplayName("a secret of exactly 32 bytes is accepted")
    void acceptsMinimumLength() {
        Assertions.assertDoesNotThrow(() -> new WorkloadSecurityProperties(THIRTY_TWO_BYTES));
    }

    @Test
    @DisplayName("the length is counted in bytes, not in characters")
    void countsBytes() {
        Assertions.assertDoesNotThrow(() -> new WorkloadSecurityProperties("\u00e9".repeat(16)));
    }

    @Test
    @DisplayName("the signing key is the secret as an HMAC SHA-256 key")
    void buildsHmacKey() {
        SecretKey key = new WorkloadSecurityProperties(THIRTY_TWO_BYTES).signingKey();

        Assertions.assertEquals("HmacSHA256", key.getAlgorithm());
        Assertions.assertArrayEquals(THIRTY_TWO_BYTES.getBytes(StandardCharsets.UTF_8), key.getEncoded());
    }
}
