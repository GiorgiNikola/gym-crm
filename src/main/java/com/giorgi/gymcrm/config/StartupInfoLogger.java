package com.giorgi.gymcrm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupInfoLogger {

    private final Environment environment;

    public StartupInfoLogger(Environment environment) {
        this.environment = environment;
    }

    // The URL only: the username and the password are separate properties and stay out of the log.
    @EventListener(ApplicationReadyEvent.class)
    public void logDatabaseTarget() {
        log.info("Started with profile(s) {} talking to {}",
                String.join(", ", profiles()), environment.getProperty("spring.datasource.url"));
    }

    private String[] profiles() {
        String[] active = environment.getActiveProfiles();
        return active.length == 0 ? environment.getDefaultProfiles() : active;
    }
}
