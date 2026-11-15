package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.model.User;

public record NewUser(User user, String rawPassword) {
}
