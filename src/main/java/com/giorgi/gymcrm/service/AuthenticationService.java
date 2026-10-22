package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.metrics.GymMetrics;
import com.giorgi.gymcrm.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class AuthenticationService {

    private UserDao userDao;
    private GymMetrics gymMetrics;

    @Autowired
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    @Autowired
    public void setGymMetrics(GymMetrics gymMetrics) {
        this.gymMetrics = gymMetrics;
    }

    @Transactional(readOnly = true)
    public void authenticate(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        Optional<User> user = userDao.findByUsername(username);
        if (user.isEmpty() || !user.get().getPassword().equals(password)) {
            gymMetrics.loginFailed();
            log.warn("Failed authentication attempt for username: {}", username);
            throw new AuthenticationException("Invalid username or password");
        }

        gymMetrics.loginSucceeded();
        log.debug("Authenticated user: {}", username);
    }
}