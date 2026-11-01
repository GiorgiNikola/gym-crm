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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingTypeServiceTest {

    @Mock
    private TrainingTypeDao trainingTypeDao;

    @InjectMocks
    private TrainingTypeService trainingTypeService;

    private TrainingType trainingType(String name) {
        TrainingType type = new TrainingType();
        ReflectionTestUtils.setField(type, "name", name);
        return type;
    }

    @Test
    @DisplayName("findAll returns the list the dao gives back")
    void findsAllTypes() {
        List<TrainingType> types = List.of(new TrainingType(), new TrainingType());
        when(trainingTypeDao.findAll()).thenReturn(types);

        Assertions.assertSame(types, trainingTypeService.findAll());
    }

    @Test
    @DisplayName("requireByName returns the stored type")
    void findsTypeByName() {
        TrainingType yoga = new TrainingType();
        when(trainingTypeDao.findByName("YOGA")).thenReturn(Optional.of(yoga));

        Assertions.assertSame(yoga, trainingTypeService.requireByName("YOGA"));
    }

    @Test
    @DisplayName("requireByName lists the valid names when the type is unknown")
    void throwsWithValidNamesForUnknownType() {
        when(trainingTypeDao.findByName("PILATES")).thenReturn(Optional.empty());
        when(trainingTypeDao.findAll()).thenReturn(List.of(trainingType("FITNESS"), trainingType("YOGA")));

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> trainingTypeService.requireByName("PILATES"));

        Assertions.assertEquals("Unknown training type: PILATES. Valid values: FITNESS, YOGA",
                thrown.getMessage());
    }
}
