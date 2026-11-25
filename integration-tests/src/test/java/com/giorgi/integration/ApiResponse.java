package com.giorgi.integration;

import java.time.Duration;

public record ApiResponse(int status, String body, String transactionId, Duration took) {
}
