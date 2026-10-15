package com.giorgi.gymcrm.config;

import com.giorgi.gymcrm.controller.TrainingTypeController;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableWebMvc
@ComponentScan(basePackageClasses = TrainingTypeController.class)
public class WebConfig {

}
