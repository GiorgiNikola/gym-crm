package com.giorgi.gymcrm.component;

import org.springframework.http.MediaType;

public record ApiResponse(int status, MediaType contentType, String body, String transactionId) {
}
