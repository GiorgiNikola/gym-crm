package com.giorgi.gymcrm.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialGeneratorTest {

    private final CredentialGenerator credentialGenerator = new CredentialGenerator();

    @Test
    @DisplayName("generateUsername joins first and last name with a dot")
    void joinsNamesWithDot() {
        Assertions.assertEquals("John.Smith", credentialGenerator.generateUsername("John", "Smith"));
        Assertions.assertEquals("Ana.Kapanadze", credentialGenerator.generateUsername("Ana", "Kapanadze"));
    }

    @Test
    @DisplayName("addSerialNumberToUsername appends the number")
    void appendsSerialNumber() {
        Assertions.assertEquals("John.Smith1", credentialGenerator.addSerialNumberToUsername("John.Smith", 1));
        Assertions.assertEquals("John.Smith12", credentialGenerator.addSerialNumberToUsername("John.Smith", 12));
    }

    @Test
    @DisplayName("generatePassword returns 10 characters from the expected alphabet")
    void generatesTenCharacterPassword() {
        for (int i = 0; i < 100; i++) {
            String password = credentialGenerator.generatePassword();
            Assertions.assertTrue(password.matches("[A-HJ-NP-Za-km-z2-9]{10}"),
                    "Unexpected password: " + password);
        }
    }
}
