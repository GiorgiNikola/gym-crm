package com.giorgi.workload.web;

import java.util.regex.Pattern;

public final class LogMasker {

    private static final Pattern MASKED_FIELD = Pattern.compile(
            "(\"\\w*(?:password|token|firstName|lastName)\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"",
            Pattern.CASE_INSENSITIVE);
    private static final String MASKED = "$1\"****\"";

    private LogMasker() {
    }

    public static String mask(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        return MASKED_FIELD.matcher(body).replaceAll(MASKED);
    }
}
