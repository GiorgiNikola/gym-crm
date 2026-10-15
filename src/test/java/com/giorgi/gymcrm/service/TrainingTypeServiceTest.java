package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainingTypeDao;
import com.giorgi.gymcrm.model.TrainingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingTypeServiceTest {

    @Mock
    private TrainingTypeDao trainingTypeDao;

    @InjectMocks
    private TrainingTypeService trainingTypeService;

    @Test
    @DisplayName("findAll returns the list the dao gives back")
    void findsAllTypes() {
        List<TrainingType> types = List.of(new TrainingType(), new TrainingType());
        when(trainingTypeDao.findAll()).thenReturn(types);

        Assertions.assertSame(types, trainingTypeService.findAll());
    }
}
