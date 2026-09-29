package com.giorgi.gymcrm.util;

public final class Validations {

    private Validations() {
    }

    public static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}