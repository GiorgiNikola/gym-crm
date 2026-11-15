package com.giorgi.gymcrm.security;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.model.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GymUserDetailsServiceTest {

    private static final String USERNAME = "John.Smith";
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv";

    @Mock
    private UserDao userDao;

    @InjectMocks
    private GymUserDetailsService userDetailsService;

    private User john(boolean active) {
        return User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Smith")
                .username(USERNAME)
                .password(HASH)
                .isActive(active)
                .build();
    }

    @Test
    @DisplayName("the stored hash is what the provider gets to compare against")
    void handsBackTheStoredHash() {
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john(true)));

        UserDetails details = userDetailsService.loadUserByUsername(USERNAME);

        Assertions.assertEquals(USERNAME, details.getUsername());
        Assertions.assertEquals(HASH, details.getPassword());
    }

    @Test
    @DisplayName("nobody gets a role: who may touch what is decided per endpoint")
    void grantsNoAuthorities() {
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john(true)));

        Assertions.assertTrue(userDetailsService.loadUserByUsername(USERNAME).getAuthorities().isEmpty());
    }

    @Test
    @DisplayName("a deactivated user is still enabled as far as logging in goes")
    void keepsDeactivatedUsersAbleToLogIn() {
        when(userDao.findByUsername(USERNAME)).thenReturn(Optional.of(john(false)));

        UserDetails details = userDetailsService.loadUserByUsername(USERNAME);

        Assertions.assertTrue(details.isEnabled());
        Assertions.assertTrue(details.isAccountNonLocked());
        Assertions.assertTrue(details.isCredentialsNonExpired());
    }

    @Test
    @DisplayName("an unknown username is a UsernameNotFoundException, which Spring then hides")
    void throwsForAnUnknownUsername() {
        when(userDao.findByUsername("Nobody.Here")).thenReturn(Optional.empty());

        UsernameNotFoundException thrown = Assertions.assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("Nobody.Here"));

        Assertions.assertEquals("No user found for username: Nobody.Here", thrown.getMessage());
    }
}
