package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainingTypeDao;
import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingTypeServiceTest {

    private static final String USERNAME = "John.Smith";
    private static final String PASSWORD = "aX7kQ2mN9p";

    @Mock
    private TrainingTypeDao trainingTypeDao;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private TrainingTypeService trainingTypeService;

    @Test
    @DisplayName("findAll authenticates and returns the list the dao gives back")
    void findsAllTypes() {
        List<TrainingType> types = List.of(new TrainingType(), new TrainingType());
        when(trainingTypeDao.findAll()).thenReturn(types);

        Assertions.assertSame(types, trainingTypeService.findAll(USERNAME, PASSWORD));
        verify(authenticationService).authenticate(USERNAME, PASSWORD);
    }

    @Test
    @DisplayName("findAll never touches the dao when the credentials are wrong")
    void skipsDaoForWrongCredentials() {
        doThrow(new AuthenticationException("Invalid username or password"))
                .when(authenticationService).authenticate(USERNAME, "wrong");

        Assertions.assertThrows(AuthenticationException.class,
                () -> trainingTypeService.findAll(USERNAME, "wrong"));
        verify(trainingTypeDao, never()).findAll();
    }
}
