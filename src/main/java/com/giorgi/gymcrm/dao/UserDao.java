package com.giorgi.gymcrm.dao;

import com.giorgi.gymcrm.model.User;

import java.util.Optional;

public interface UserDao {
    boolean existsByUsername(String username);
    Optional<User> findByUsername(String username);
}