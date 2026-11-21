package com.giorgi.workload.messaging;

import com.giorgi.workload.TestTokens;
import com.giorgi.workload.security.WorkloadSecurityProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

class ServiceTokenVerifierTest {

    private static final Instant SENT_AT = Instant.parse("2026-11-20T10:00:00Z");

    private final ServiceTokenVerifier verifier =
            new ServiceTokenVerifier(new WorkloadSecurityProperties(TestTokens.SECRET));

    private static TestTokens.Builder writeToken() {
        return TestTokens.token().scope(TestTokens.WRITE).issuedAt(SENT_AT).expiresAt(SENT_AT.plusSeconds(300));
    }

    private RejectedEventException rejected(String token, Long sentAtMillis) {
        return Assertions.assertThrows(RejectedEventException.class, () -> verifier.verify(token, sentAtMillis));
    }

    @Test
    @DisplayName("a token valid when the message was sent passes long after it expired")
    void acceptsTokenValidAtSendTime() {
        Assertions.assertDoesNotThrow(() -> verifier.verify(writeToken().sign(), SENT_AT.toEpochMilli()));
    }

    @Test
    @DisplayName("a token that expired more than the skew before the send is unauthenticated")
    void rejectsTokenExpiredBeforeSend() {
        String token = writeToken().issuedAt(SENT_AT.minusSeconds(600)).expiresAt(SENT_AT.minusSeconds(61)).sign();

        RejectedEventException exception = rejected(token, SENT_AT.toEpochMilli());

        Assertions.assertEquals(DeadLetterReason.UNAUTHENTICATED, exception.reason());
        Assertions.assertTrue(exception.getMessage().startsWith("Jwt expired at "), exception.getMessage());
        Assertions.assertTrue(exception.getMessage().endsWith(", message sent at " + SENT_AT), exception.getMessage());
    }

    @Test
    @DisplayName("expiry within the skew of the send still passes")
    void toleratesExpiryWithinSkew() {
        String token = writeToken().issuedAt(SENT_AT.minusSeconds(600)).expiresAt(SENT_AT.minusSeconds(59)).sign();

        Assertions.assertDoesNotThrow(() -> verifier.verify(token, SENT_AT.toEpochMilli()));
    }

    @Test
    @DisplayName("a token not yet valid when the message was sent is unauthenticated")
    void rejectsTokenNotYetValidAtSend() {
        String token = writeToken().notBefore(SENT_AT.plusSeconds(120)).sign();

        RejectedEventException exception = rejected(token, SENT_AT.toEpochMilli());

        Assertions.assertEquals(DeadLetterReason.UNAUTHENTICATED, exception.reason());
        Assertions.assertTrue(exception.getMessage().startsWith("Jwt used before "), exception.getMessage());
    }

    @Test
    @DisplayName("a token without an expiry is unauthenticated")
    void rejectsTokenWithoutExpiry() {
        RejectedEventException exception = rejected(writeToken().expiresAt(null).sign(), SENT_AT.toEpochMilli());

        Assertions.assertEquals(DeadLetterReason.UNAUTHENTICATED, exception.reason());
    }

    @Test
    @DisplayName("a missing or zero message timestamp is unauthenticated before the token is read")
    void rejectsMissingTimestamp() {
        for (Long sentAtMillis : new Long[] {null, 0L, -1L}) {
            RejectedEventException exception = rejected("not-even-a-jwt", sentAtMillis);

            Assertions.assertEquals(DeadLetterReason.UNAUTHENTICATED, exception.reason());
            Assertions.assertEquals("message timestamp is missing, so the token cannot be checked",
                    exception.getMessage());
        }
    }

    @Test
    @DisplayName("the signature, issuer and audience are still checked")
    void stillChecksSignatureIssuerAndAudience() {
        long sentAt = SENT_AT.toEpochMilli();

        Assertions.assertTrue(rejected(writeToken().secret("another-secret-that-is-long-enough-0123456789").sign(),
                sentAt).getMessage().contains("Invalid signature"));
        Assertions.assertTrue(rejected(writeToken().issuer("someone-else").sign(), sentAt).getMessage()
                .contains("iss"));
        Assertions.assertTrue(rejected(writeToken().audience("gym-crm").sign(), sentAt).getMessage()
                .contains("aud"));
    }

    @Test
    @DisplayName("a token without the write scope is forbidden, also with the Bearer prefix")
    void forbidsReadOnlyToken() {
        RejectedEventException exception = rejected("Bearer " + writeToken().scope(TestTokens.READ).sign(),
                SENT_AT.toEpochMilli());

        Assertions.assertEquals(DeadLetterReason.FORBIDDEN, exception.reason());
    }
}
