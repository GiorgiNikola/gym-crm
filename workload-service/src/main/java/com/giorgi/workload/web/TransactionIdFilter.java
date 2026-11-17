package com.giorgi.workload.web;

import com.giorgi.workload.logging.TransactionIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TransactionIdFilter extends OncePerRequestFilter {

    public static final String TRANSACTION_ID_HEADER = "X-Transaction-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String transactionId = TransactionIds.resolve(request.getHeader(TRANSACTION_ID_HEADER));
        MDC.put(TransactionIds.MDC_KEY, transactionId);
        response.setHeader(TRANSACTION_ID_HEADER, transactionId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TransactionIds.MDC_KEY);
        }
    }
}
