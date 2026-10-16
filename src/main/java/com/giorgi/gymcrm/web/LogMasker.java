package com.giorgi.gymcrm.web;

import java.util.regex.Pattern;

public final class LogMasker {

    private static final Pattern PASSWORD_FIELD =
            Pattern.compile("(\"\\w*password\"\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"", Pattern.CASE_INSENSITIVE);
    private static final String MASKED = "$1\"****\"";

    private LogMasker() {
    }

    public static String mask(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        return PASSWORD_FIELD.matcher(body).replaceAll(MASKED);
    }
}
