package com.giorgi.gymcrm.workload;

import com.giorgi.gymcrm.web.TransactionIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class TransactionIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String transactionId = MDC.get(TransactionIdFilter.MDC_KEY);
        if (transactionId != null) {
            request.getHeaders().set(TransactionIdFilter.TRANSACTION_ID_HEADER, transactionId);
        }
        return execution.execute(request, body);
    }
}
