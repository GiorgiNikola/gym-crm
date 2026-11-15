package com.giorgi.gymcrm.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final TokenClaims claims;

    public JwtAuthenticationToken(TokenClaims claims) {
        super(List.of());
        this.claims = claims;
        setAuthenticated(true);
    }

    public TokenClaims claims() {
        return claims;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return claims.username();
    }
}
