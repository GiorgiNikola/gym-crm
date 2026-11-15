package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainingTypeDao;
import com.giorgi.gymcrm.model.TrainingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrainingTypeService {

    private final TrainingTypeDao trainingTypeDao;

    public TrainingTypeService(TrainingTypeDao trainingTypeDao) {
        this.trainingTypeDao = trainingTypeDao;
    }

    @Transactional(readOnly = true)
    public List<TrainingType> findAll() {
        List<TrainingType> types = trainingTypeDao.findAll();
        log.debug("Listing {} training types", types.size());
        return types;
    }

    @Transactional(readOnly = true)
    public TrainingType requireByName(String name) {
        return trainingTypeDao.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown training type: " + name + ". Valid values: " + validNames()));
    }

    private String validNames() {
        return trainingTypeDao.findAll().stream()
                .map(TrainingType::getName)
                .collect(Collectors.joining(", "));
    }
}
