package com.giorgi.gymcrm.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class TransactionIdFilterTest {

    private final TransactionIdFilter filter = new TransactionIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private String responseId() {
        return response.getHeader(TransactionIdFilter.TRANSACTION_ID_HEADER);
    }

    @Test
    @DisplayName("a request without the header gets a generated id")
    void generatesIdWhenHeaderIsMissing() throws Exception {
        filter.doFilter(request, response, (req, res) -> {
        });

        Assertions.assertNotNull(responseId());
        Assertions.assertDoesNotThrow(() -> UUID.fromString(responseId()));
    }

    @Test
    @DisplayName("a clean incoming id is kept")
    void reusesIncomingId() throws Exception {
        request.addHeader(TransactionIdFilter.TRANSACTION_ID_HEADER, "abc-123");

        filter.doFilter(request, response, (req, res) -> {
        });

        Assertions.assertEquals("abc-123", responseId());
    }

    @Test
    @DisplayName("an incoming id with a line break is thrown away")
    void replacesInjectedId() throws Exception {
        request.addHeader(TransactionIdFilter.TRANSACTION_ID_HEADER, "abc\nINFO pretend log line");

        filter.doFilter(request, response, (req, res) -> {
        });

        Assertions.assertDoesNotThrow(() -> UUID.fromString(responseId()));
    }

    @Test
    @DisplayName("the chain sees the id in the MDC and the MDC is clean afterwards")
    void keepsIdInMdcForTheChain() throws Exception {
        AtomicReference<String> insideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> insideChain.set(MDC.get(TransactionIdFilter.MDC_KEY)));

        Assertions.assertEquals(responseId(), insideChain.get());
        Assertions.assertNull(MDC.get(TransactionIdFilter.MDC_KEY));
    }

    @Test
    @DisplayName("the MDC is cleaned up even when the chain blows up")
    void clearsMdcWhenChainThrows() {
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        Assertions.assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, failing));
        Assertions.assertNull(MDC.get(TransactionIdFilter.MDC_KEY));
    }
}
