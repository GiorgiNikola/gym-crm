package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.exception.AuthenticationException;
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

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private UserDao userDao;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private UserService userService;

    private User john(boolean active) {
        return User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Smith")
                .username(USERNAME)
                .password(PASSWORD)
                .isActive(active)
                .build();
    }

    @Test
    @DisplayName("activating an inactive user sets the flag")
    void activatesInactiveUser() {
        User john = john(false);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        userService.changeStatus(USERNAME, PASSWORD, true);

        Assertions.assertTrue(john.isActive());
        verify(authenticationService).authenticate(USERNAME, PASSWORD);
    }

    @Test
    @DisplayName("deactivating an active user clears the flag")
    void deactivatesActiveUser() {
        User john = john(true);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        userService.changeStatus(USERNAME, PASSWORD, false);

        Assertions.assertFalse(john.isActive());
    }

    @Test
    @DisplayName("activating a user who is already active is a conflict")
    void activeUserCannotBeActivatedAgain() {
        User john = john(true);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        Assertions.assertThrows(StateConflictException.class,
                () -> userService.changeStatus(USERNAME, PASSWORD, true));

        Assertions.assertTrue(john.isActive());
    }

    @Test
    @DisplayName("deactivating a user who is already inactive is a conflict")
    void inactiveUserCannotBeDeactivatedAgain() {
        User john = john(false);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        Assertions.assertThrows(StateConflictException.class,
                () -> userService.changeStatus(USERNAME, PASSWORD, false));

        Assertions.assertFalse(john.isActive());
    }

    @Test
    @DisplayName("changeStatus throws when the user does not exist")
    void statusChangeForMissingUser() {
        when(userDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> userService.changeStatus("Nobody.Here", PASSWORD, true));
    }

    @Test
    @DisplayName("a failed authentication stops the status change before the dao is touched")
    void statusChangeNeedsValidCredentials() {
        doThrow(new AuthenticationException("Invalid username or password"))
                .when(authenticationService).authenticate(USERNAME, "wrongPassword");

        Assertions.assertThrows(AuthenticationException.class,
                () -> userService.changeStatus(USERNAME, "wrongPassword", false));

        verifyNoInteractions(userDao);
    }

    @Test
    @DisplayName("changePassword stores the new password")
    void changesPassword() {
        User john = john(true);
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john));

        userService.changePassword(USERNAME, PASSWORD, "newPassword1");

        Assertions.assertEquals("newPassword1", john.getPassword());
        verify(authenticationService).authenticate(USERNAME, PASSWORD);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("changePassword rejects a blank new password")
    void rejectsBlankNewPassword(String newPassword) {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(USERNAME, PASSWORD, newPassword));

        verifyNoInteractions(userDao);
    }

    @Test
    @DisplayName("changePassword throws when the user does not exist")
    void passwordChangeForMissingUser() {
        when(userDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        Assertions.assertThrows(ProfileNotFoundException.class,
                () -> userService.changePassword("Nobody.Here", PASSWORD, "newPassword1"));
    }

    @Test
    @DisplayName("a failed authentication leaves the password untouched")
    void passwordChangeNeedsValidCredentials() {
        doThrow(new AuthenticationException("Invalid username or password"))
                .when(authenticationService).authenticate(USERNAME, "wrongPassword");

        Assertions.assertThrows(AuthenticationException.class,
                () -> userService.changePassword(USERNAME, "wrongPassword", "newPassword1"));

        verifyNoInteractions(userDao);
    }
}