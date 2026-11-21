package com.giorgi.workload.component;

import org.springframework.http.MediaType;

public record ApiResponse(int status, MediaType contentType, String body) {
}
