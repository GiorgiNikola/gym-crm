package com.giorgi.gymcrm;

import com.giorgi.gymcrm.config.RootConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RootConfig.class)
class GymCrmApplicationTests {

    @Test
    void contextLoads() {
    }

}
