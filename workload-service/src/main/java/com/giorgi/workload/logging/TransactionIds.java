package com.giorgi.workload.logging;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TransactionIds {

    public static final String MDC_KEY = "transactionId";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private TransactionIds() {
    }

    public static String resolve(String incoming) {
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
