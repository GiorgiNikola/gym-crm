package com.giorgi.gymcrm.workload;

import com.giorgi.gymcrm.web.TransactionIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkloadInterceptorsTest {

    private static final byte[] BODY = "{}".getBytes();

    private final MockClientHttpRequest request =
            new MockClientHttpRequest(HttpMethod.POST, URI.create("http://workload-service/workloads"));
    private final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    private final MockClientHttpResponse response = new MockClientHttpResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("the service token goes out as a bearer token")
    void addsTheBearerToken() throws Exception {
        ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
        when(tokens.readToken()).thenReturn("service.token.value");
        when(execution.execute(request, BODY)).thenReturn(response);

        Assertions.assertSame(response, new ServiceTokenInterceptor(tokens).intercept(request, BODY, execution));

        Assertions.assertEquals("Bearer service.token.value", request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        verify(execution).execute(request, BODY);
    }

    @Test
    @DisplayName("the transaction id on the MDC goes out as X-Transaction-Id")
    void copiesTheTransactionId() throws Exception {
        MDC.put(TransactionIdFilter.MDC_KEY, "4f1c2d3e-aaaa-bbbb-cccc-0123456789ab");
        when(execution.execute(request, BODY)).thenReturn(response);

        Assertions.assertSame(response, new TransactionIdInterceptor().intercept(request, BODY, execution));

        Assertions.assertEquals("4f1c2d3e-aaaa-bbbb-cccc-0123456789ab",
                request.getHeaders().getFirst(TransactionIdFilter.TRANSACTION_ID_HEADER));
    }

    @Test
    @DisplayName("without a transaction id on the MDC no header is invented")
    void sendsNoHeaderWithoutATransactionId() throws Exception {
        when(execution.execute(request, BODY)).thenReturn(response);

        new TransactionIdInterceptor().intercept(request, BODY, execution);

        Assertions.assertFalse(request.getHeaders().containsHeader(TransactionIdFilter.TRANSACTION_ID_HEADER));
        verify(execution).execute(request, BODY);
    }
}
