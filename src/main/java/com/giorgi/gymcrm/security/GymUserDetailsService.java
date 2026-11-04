package com.giorgi.gymcrm.security;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class GymUserDetailsService implements UserDetailsService {

    private final UserDao userDao;

    public GymUserDetailsService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userDao.findByUsername(username)
                .map(GymUserDetailsService::toUserDetails)
                .orElseThrow(() -> {
                    log.warn("No user found for username: {}", username);
                    return new UsernameNotFoundException("No user found for username: " + username);
                });
    }

    // isActive is deliberately not mapped onto enabled: a deactivated user must still be able to log in.
    private static UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of())
                .build();
    }
}
