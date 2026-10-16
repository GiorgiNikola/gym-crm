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
    private AuthenticationService authenticationService;

    @Autowired
    public void setTrainingTypeDao(TrainingTypeDao trainingTypeDao) {
        this.trainingTypeDao = trainingTypeDao;
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Transactional(readOnly = true)
    public List<TrainingType> findAll(String username, String password) {
        authenticationService.authenticate(username, password);

        List<TrainingType> types = trainingTypeDao.findAll();
        log.debug("Listing {} training types", types.size());
        return types;
    }
}
