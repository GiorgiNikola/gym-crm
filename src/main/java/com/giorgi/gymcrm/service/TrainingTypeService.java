package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.TrainingTypeDao;
import com.giorgi.gymcrm.model.TrainingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class TrainingTypeService {

    private TrainingTypeDao trainingTypeDao;

    @Autowired
    public void setTrainingTypeDao(TrainingTypeDao trainingTypeDao) {
        this.trainingTypeDao = trainingTypeDao;
    }

    @Transactional(readOnly = true)
    public List<TrainingType> findAll() {
        List<TrainingType> types = trainingTypeDao.findAll();
        log.debug("Listing {} training types", types.size());
        return types;
    }
}
