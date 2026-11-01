package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.util.CredentialGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFactoryTest {

    private static final String RAW_PASSWORD = "aX7kQ2mN9p";

    @Mock
    private UsernameResolver usernameResolver;

    @Mock
    private CredentialGenerator credentialGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserFactory userFactory;

    @Test
    @DisplayName("the username comes from the resolver and the password from the generator")
    void buildsUserFromTheGenerators() {
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn(RAW_PASSWORD);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$10$hashed");

        NewUser created = userFactory.create("John", "Smith");

        Assertions.assertEquals("John.Smith", created.user().getUsername());
        Assertions.assertEquals("John", created.user().getFirstName());
        Assertions.assertEquals("Smith", created.user().getLastName());
        Assertions.assertTrue(created.user().isActive());
    }

    @Test
    @DisplayName("the entity carries the hash while the plain password is handed back separately")
    void storesTheHashAndReturnsThePlainPassword() {
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn(RAW_PASSWORD);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("$2a$10$hashed");

        NewUser created = userFactory.create("John", "Smith");

        Assertions.assertEquals("$2a$10$hashed", created.user().getPassword());
        Assertions.assertEquals(RAW_PASSWORD, created.rawPassword());
    }

    @Test
    @DisplayName("a real BCrypt hash matches the generated password and does not contain it")
    void producesAVerifiableBcryptHash() {
        PasswordEncoder bcrypt = new BCryptPasswordEncoder();
        ReflectionTestUtils.setField(userFactory, "passwordEncoder", bcrypt);
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn(RAW_PASSWORD);

        NewUser created = userFactory.create("John", "Smith");
        String stored = created.user().getPassword();

        Assertions.assertTrue(stored.startsWith("$2a$"), stored);
        Assertions.assertFalse(stored.contains(RAW_PASSWORD), stored);
        Assertions.assertTrue(bcrypt.matches(RAW_PASSWORD, stored));
    }

    @Test
    @DisplayName("the same password hashes differently every time, so BCrypt is salting")
    void saltsEveryHash() {
        PasswordEncoder bcrypt = new BCryptPasswordEncoder();
        ReflectionTestUtils.setField(userFactory, "passwordEncoder", bcrypt);
        when(usernameResolver.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.generatePassword()).thenReturn(RAW_PASSWORD);

        String first = userFactory.create("John", "Smith").user().getPassword();
        String second = userFactory.create("John", "Smith").user().getPassword();

        Assertions.assertNotEquals(first, second);
    }
}
