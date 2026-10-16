package com.giorgi.gymcrm.web;

import com.giorgi.gymcrm.exception.ForbiddenOperationException;

public final class RequestGuards {

    private RequestGuards() {
    }

    public static void requireSelf(String pathUsername, Credentials credentials) {
        if (!credentials.username().equals(pathUsername)) {
            throw new ForbiddenOperationException("Credentials do not belong to user " + pathUsername);
        }
    }
}
