package com.giorgi.gymcrm.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CredentialGeneratorTest {
    private final CredentialGenerator credentialGenerator = new CredentialGenerator();

    @ParameterizedTest
    @CsvSource({
            "John, Smith, John.Smith",
            "Ana, Kapanadze, Ana.Kapanadze",
            "Emily, Johnson, Emily.Johnson"
    })
    @DisplayName("joins first and last name with a dot")
    void buildsUsernameFromNames(String firstName, String lastName, String expected) {
        Assertions.assertEquals(expected, credentialGenerator.generateUsername(firstName, lastName));
    }

    @ParameterizedTest
    @CsvSource({"1, John.Smith1", "2, John.Smith2", "10, John.Smith10"})
    @DisplayName("appends the serial number to the username")
    void appendsSerialNumber(int serialNumber, String expected) {
        Assertions.assertEquals(expected, credentialGenerator.addSerialNumberToUsername("John.Smith", serialNumber));
    }

    @Test
    @DisplayName("generates a 10 char password without ambiguous characters")
    void generatesPassword() {
        for (int i = 0; i < 100; i++) {
            String password = credentialGenerator.generatePassword();
            Assertions.assertTrue(password.matches("[A-HJ-NP-Za-km-z2-9]{10}"),
                    "Unexpected password: " + password);
        }
    }
}
