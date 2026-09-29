package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.model.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserDao userDao;

    @InjectMocks
    private AuthenticationService authenticationService;

    private User john() {
        return User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Smith")
                .username("John.Smith")
                .password("aX7kQ2mN9p")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("passes for a matching username and password")
    void acceptsMatchingCredentials() {
        when(userDao.findByUsername("John.Smith")).thenReturn(Optional.of(john()));

        Assertions.assertDoesNotThrow(() -> authenticationService.authenticate("John.Smith", "aX7kQ2mN9p"));
    }

    @Test
    @DisplayName("throws for a wrong password")
    void rejectsWrongPassword() {
        when(userDao.findByUsername("John.Smith")).thenReturn(Optional.of(john()));

        Assertions.assertThrows(AuthenticationException.class,
                () -> authenticationService.authenticate("John.Smith", "wrongPassword"));
    }

    @Test
    @DisplayName("throws for an unknown username")
    void rejectsUnknownUsername() {
        when(userDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(AuthenticationException.class,
                () -> authenticationService.authenticate("Nobody.Here", "aX7kQ2mN9p"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("rejects a blank username without hitting the dao")
    void rejectsBlankUsername(String username) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> authenticationService.authenticate(username, "aX7kQ2mN9p"));

        verifyNoInteractions(userDao);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("rejects a blank password without hitting the dao")
    void rejectsBlankPassword(String password) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> authenticationService.authenticate("John.Smith", password));

        verifyNoInteractions(userDao);
    }
}
