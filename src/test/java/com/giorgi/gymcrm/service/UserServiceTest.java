package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.exception.StateConflictException;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String USERNAME = "John.Smith";
    private static final String OLD_PASSWORD = "aX7kQ2mN9p";
    private static final String OLD_HASH = "$2a$10$oldoldoldoldoldoldold";
    private static final String NEW_PASSWORD = "bY8lR3nO0q";
    private static final String NEW_HASH = "$2a$10$newnewnewnewnewnewnew";

    @Mock
    private UserDao userDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User john(boolean active) {
        return User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Smith")
                .username(USERNAME)
                .password(OLD_HASH)
                .isActive(active)
                .build();
    }

    @Test
    @DisplayName("activating an inactive user sets the flag")
    void activatesInactiveUser() {
        User john = john(false);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        userService.changeStatus(USERNAME, true);

        Assertions.assertTrue(john.isActive());
    }

    @Test
    @DisplayName("deactivating an active user clears the flag")
    void deactivatesActiveUser() {
        User john = john(true);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        userService.changeStatus(USERNAME, false);

        Assertions.assertFalse(john.isActive());
    }

    @Test
    @DisplayName("activating a user who is already active is a conflict")
    void activeUserCannotBeActivatedAgain() {
        User john = john(true);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        Assertions.assertThrows(StateConflictException.class, () -> userService.changeStatus(USERNAME, true));

        Assertions.assertTrue(john.isActive());
    }

    @Test
    @DisplayName("deactivating a user who is already inactive is a conflict")
    void inactiveUserCannotBeDeactivatedAgain() {
        User john = john(false);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        Assertions.assertThrows(StateConflictException.class, () -> userService.changeStatus(USERNAME, false));

        Assertions.assertFalse(john.isActive());
    }

    @Test
    @DisplayName("changeStatus throws when the user does not exist")
    void statusChangeForMissingUser() {
        when(userDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> userService.changeStatus("Nobody.Here", true));
    }

    @Test
    @DisplayName("changePassword stores the hash of the new password")
    void changesPassword() {
        User john = john(true);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));
        when(passwordEncoder.matches(OLD_PASSWORD, OLD_HASH)).thenReturn(true);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

        userService.changePassword(USERNAME, OLD_PASSWORD, NEW_PASSWORD);

        Assertions.assertEquals(NEW_HASH, john.getPassword());
    }

    @Test
    @DisplayName("a wrong old password leaves the stored hash alone")
    void rejectsWrongOldPassword() {
        User john = john(true);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));
        when(passwordEncoder.matches("wrongPassword", OLD_HASH)).thenReturn(false);

        Assertions.assertThrows(BadCredentialsException.class,
                () -> userService.changePassword(USERNAME, "wrongPassword", NEW_PASSWORD));

        Assertions.assertEquals(OLD_HASH, john.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("changePassword rejects a blank new password")
    void rejectsBlankNewPassword(String newPassword) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(USERNAME, OLD_PASSWORD, newPassword));

        verifyNoInteractions(userDao, passwordEncoder);
    }

    @Test
    @DisplayName("changePassword throws when the user does not exist")
    void passwordChangeForMissingUser() {
        when(userDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> userService.changePassword("Nobody.Here", OLD_PASSWORD, NEW_PASSWORD));

        verifyNoInteractions(passwordEncoder);
    }
}
