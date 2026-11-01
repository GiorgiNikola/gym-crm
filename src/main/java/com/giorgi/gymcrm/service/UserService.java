package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.exception.StateConflictException;
import com.giorgi.gymcrm.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.giorgi.gymcrm.util.Validations.requireText;

@Slf4j
@Service
public class UserService {

    private UserDao userDao;
    private PasswordEncoder passwordEncoder;

    @Autowired
    public void setUserDao(UserDao userDao) {
        this.userDao = userDao;
    }

    @Autowired
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changeStatus(String username, boolean active) {
        User user = findOrThrow(username);

        if (user.isActive() == active) {
            throw new StateConflictException("User " + username + " is already " + (active ? "active" : "inactive"));
        }

        user.setActive(active);
        log.info("User {} is {}", username, active ? "activated" : "deactivated");
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        requireText(newPassword, "New password");

        User user = findOrThrow(username);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("Rejected a password change for user {}: the old password did not match", username);
            throw new BadCredentialsException("The old password is not correct");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        log.info("Password changed for user: {}", username);
    }

    private User findOrThrow(String username) {
        return userDao.findByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("User not found: " + username));
    }
}
