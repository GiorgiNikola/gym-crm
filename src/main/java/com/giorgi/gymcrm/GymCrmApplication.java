package com.giorgi.gymcrm;

import com.giorgi.gymcrm.config.RootConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class GymCrmApplication {

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(RootConfig.class)){}
    }

}
