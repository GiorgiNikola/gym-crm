package com.giorgi.gymcrm.config;

import com.giorgi.gymcrm.dao.TraineeDao;
import com.giorgi.gymcrm.demo.DemoRunner;
import com.giorgi.gymcrm.facade.GymCrmFacade;
import com.giorgi.gymcrm.service.TraineeService;
import com.giorgi.gymcrm.util.CredentialGenerator;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan(basePackageClasses = {
        TraineeDao.class,
        TraineeService.class,
        GymCrmFacade.class,
        CredentialGenerator.class,
        DemoRunner.class
})
@Import(PersistenceConfig.class)
public class RootConfig {
}
