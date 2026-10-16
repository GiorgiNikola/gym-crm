package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.exception.StateConflictException;
import com.giorgi.gymcrm.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.giorgi.gymcrm.util.Validations.requireText;

@Slf4j
@Service
public class UserService {

    private UserDao userDao;
    private AuthenticationService authenticationService;

    @Autowired
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Transactional(readOnly = true)
    public void login(String username, String password) {
        authenticationService.authenticate(username, password);
    }

    @Transactional
    public void changeStatus(String username, String password, boolean active) {
        authenticationService.authenticate(username, password);
        User user = findOrThrow(username);

        if (user.isActive() == active) {
            throw new StateConflictException("User " + username + " is already " + (active ? "active" : "inactive"));
        }

        user.setActive(active);
        log.info("User {} is {}", username,(active ? "activated" : "deactivated"));
    }

    @Transactional
    public void changePassword(String username, String password, String newPassword) {
        authenticationService.authenticate(username, password);
        requireText(newPassword, "New password");

        User user = findOrThrow(username);
        user.setPassword(newPassword);
        log.info("Password changed for User: {}", username);
    }

    private User findOrThrow(String username) {
        return userDao.findByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("User not found: " + username));
    }
}
