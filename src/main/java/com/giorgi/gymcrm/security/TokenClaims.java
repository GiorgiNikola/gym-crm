package com.giorgi.gymcrm.security;

import java.time.Instant;

public record TokenClaims(String username, String id, Instant expiresAt) {
}
