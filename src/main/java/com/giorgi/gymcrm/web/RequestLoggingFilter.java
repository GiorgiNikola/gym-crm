package com.giorgi.gymcrm.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final int CACHE_LIMIT = 10 * 1024;
    private static final String ACTUATOR_PREFIX = "/actuator/";

    // Prometheus scrapes every few seconds; logging that would bury the real traffic.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(ACTUATOR_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request, CACHE_LIMIT);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            logCall(cachedRequest, cachedResponse, System.currentTimeMillis() - startedAt);
            cachedResponse.copyBodyToResponse();
        }
    }

    private void logCall(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long tookMs) {
        String call = request.getMethod() + " " + fullUri(request);
        int status = response.getStatus();

        if (status >= 500) {
            log.error("{} failed with {} in {} ms", call, status, tookMs);
        } else if (status >= 400) {
            log.warn("{} rejected with {} in {} ms, response {}", call, status, tookMs,
                    body(response.getContentAsByteArray()));
        } else {
            log.info("{} returned {} in {} ms", call, status, tookMs);
        }

        if (log.isDebugEnabled()) {
            log.debug("{} request body {}", call, body(request.getContentAsByteArray()));
            log.debug("{} response body {}", call, body(response.getContentAsByteArray()));
        }
    }

    private String fullUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String body(byte[] content) {
        return LogMasker.mask(new String(content, StandardCharsets.UTF_8));
    }
}
