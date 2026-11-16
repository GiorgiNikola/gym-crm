package com.giorgi.workload.web;

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
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final int CACHE_LIMIT = 10 * 1024;
    private static final String NO_BODY = "-";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final List<String> UNLOGGED_PREFIXES = List.of(
            "/actuator/", "/swagger-ui.html", "/swagger-ui/", "/v3/api-docs");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return UNLOGGED_PREFIXES.stream().anyMatch(uri::startsWith);
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
        String requestBody = body(request.getContentAsByteArray());

        if (status >= 500) {
            log.error("{} failed with {} in {} ms, request {}, response {}", call, status, tookMs,
                    requestBody, body(response.getContentAsByteArray()));
        } else if (status >= 400) {
            log.warn("{} rejected with {} in {} ms, request {}, response {}", call, status, tookMs,
                    requestBody, body(response.getContentAsByteArray()));
        } else {
            log.info("{} returned {} in {} ms, request {}", call, status, tookMs, requestBody);
        }
    }

    private String fullUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String body(byte[] content) {
        if (content.length == 0) {
            return NO_BODY;
        }
        String singleLine = WHITESPACE.matcher(new String(content, StandardCharsets.UTF_8)).replaceAll(" ");
        return LogMasker.mask(singleLine);
    }
}
