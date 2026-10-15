package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.util.CredentialGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsernameResolverTest {

    @Mock
    private UserDao userDao;

    @Mock
    private CredentialGenerator credentialGenerator;

    @InjectMocks
    private UsernameResolver usernameResolver;

    @Test
    @DisplayName("returns First.Last when nobody has that username")
    void returnsBaseUsernameWhenFree() {
        when(credentialGenerator.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(userDao.existsByUsername("John.Smith")).thenReturn(false);

        Assertions.assertEquals("John.Smith", usernameResolver.generateUsername("John", "Smith"));
        verify(credentialGenerator, never()).addSerialNumberToUsername(anyString(), anyInt());
    }

    @Test
    @DisplayName("appends 1 when the base username is taken")
    void appendsOneOnFirstCollision() {
        when(credentialGenerator.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.addSerialNumberToUsername("John.Smith", 1)).thenReturn("John.Smith1");
        when(userDao.existsByUsername("John.Smith")).thenReturn(true);
        when(userDao.existsByUsername("John.Smith1")).thenReturn(false);

        Assertions.assertEquals("John.Smith1", usernameResolver.generateUsername("John", "Smith"));
    }

    @Test
    @DisplayName("keeps incrementing until it finds a free username")
    void countsUpUntilFree() {
        when(credentialGenerator.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.addSerialNumberToUsername("John.Smith", 1)).thenReturn("John.Smith1");
        when(credentialGenerator.addSerialNumberToUsername("John.Smith", 2)).thenReturn("John.Smith2");
        when(credentialGenerator.addSerialNumberToUsername("John.Smith", 3)).thenReturn("John.Smith3");
        when(userDao.existsByUsername("John.Smith")).thenReturn(true);
        when(userDao.existsByUsername("John.Smith1")).thenReturn(true);
        when(userDao.existsByUsername("John.Smith2")).thenReturn(true);
        when(userDao.existsByUsername("John.Smith3")).thenReturn(false);

        Assertions.assertEquals("John.Smith3", usernameResolver.generateUsername("John", "Smith"));
    }

    @Test
    @DisplayName("always suffixes the base username, never the previous candidate")
    void suffixesBaseUsernameOnly() {
        when(credentialGenerator.generateUsername("John", "Smith")).thenReturn("John.Smith");
        when(credentialGenerator.addSerialNumberToUsername("John.Smith", 1)).thenReturn("John.Smith1");
        when(credentialGenerator.addSerialNumberToUsername("John.Smith", 2)).thenReturn("John.Smith2");
        when(userDao.existsByUsername("John.Smith")).thenReturn(true);
        when(userDao.existsByUsername("John.Smith1")).thenReturn(true);
        when(userDao.existsByUsername("John.Smith2")).thenReturn(false);

        usernameResolver.generateUsername("John", "Smith");

        verify(credentialGenerator).addSerialNumberToUsername("John.Smith", 2);
        verify(credentialGenerator, never()).addSerialNumberToUsername("John.Smith1", 2);
    }
}
