package com.giorgi.integration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

// Well formed and in date, with the claims gym-crm would send, but signed with a key workload-service does not know.
public final class ForgedToken {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private ForgedToken() {
    }

    public static String writeToken() {
        long now = Instant.now().getEpochSecond();
        String header = encode("{\"alg\":\"HS256\"}");
        String claims = encode("""
                {"sub":"gym-crm","iss":"gym-crm","aud":"workload-service","scope":"workload:write",\
                "iat":%d,"exp":%d}""".formatted(now, now + 300));
        return header + "." + claims + "." + sign(header + "." + claims);
    }

    private static String encode(String text) {
        return BASE64_URL.encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("not-the-workload-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            return BASE64_URL.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
