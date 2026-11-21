package com.giorgi.workload;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class TestTokens {

    public static final String SECRET = "test-only-workload-secret-0123456789abcdef";
    public static final String SUBJECT = "gym-crm";
    public static final String WRITE = "workload:write";
    public static final String READ = "workload:read";

    private TestTokens() {
    }

    public static Builder token() {
        return new Builder();
    }

    public static String bearer(String scope) {
        return token().scope(scope).bearer();
    }

    public static final class Builder {

        private String secret = SECRET;
        private String issuer = "gym-crm";
        private List<String> audience = List.of("workload-service");
        private String scope;
        private Instant issuedAt = Instant.now();
        private Instant expiresAt = issuedAt.plusSeconds(300);
        private Instant notBefore;

        private Builder() {
        }

        public Builder secret(String secret) {
            this.secret = secret;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String... audience) {
            this.audience = audience.length == 0 ? null : List.of(audience);
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder notBefore(Instant notBefore) {
            this.notBefore = notBefore;
            return this;
        }

        public String sign() {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(SUBJECT)
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(issuedAt))
                    .notBeforeTime(notBefore == null ? null : Date.from(notBefore))
                    .expirationTime(expiresAt == null ? null : Date.from(expiresAt))
                    .claim("scope", scope)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            try {
                jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            } catch (JOSEException exception) {
                throw new IllegalStateException(exception);
            }
            return jwt.serialize();
        }

        public String bearer() {
            return "Bearer " + sign();
        }
    }
}
