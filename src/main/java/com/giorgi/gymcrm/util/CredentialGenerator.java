package com.giorgi.gymcrm.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CredentialGenerator {
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateUsername(String firstName, String lastName) {
        return firstName + "." + lastName;
    }

    public String addSerialNumberToUsername(String username, int serialNumber) {
        return username + serialNumber;
    }

    public String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
