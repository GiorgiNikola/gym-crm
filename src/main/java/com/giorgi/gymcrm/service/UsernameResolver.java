package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.dao.UserDao;
import com.giorgi.gymcrm.util.CredentialGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UsernameResolver {

    private final UserDao userDao;
    private final CredentialGenerator credentialGenerator;

    public UsernameResolver(UserDao userDao,
                            CredentialGenerator credentialGenerator) {
        this.userDao = userDao;
        this.credentialGenerator = credentialGenerator;
    }

    public String generateUsername(String firstName, String lastName) {
        String baseUsername = credentialGenerator.generateUsername(firstName, lastName);

        if (!userDao.existsByUsername(baseUsername)) {
            log.debug("Resolved username: {}", baseUsername);
            return baseUsername;
        }

        int serialNumber = 1;
        String candidate = credentialGenerator.addSerialNumberToUsername(baseUsername, serialNumber);
        while (userDao.existsByUsername(candidate)) {
            log.debug("Username {} already taken, trying next candidate", candidate);
            serialNumber++;
            candidate = credentialGenerator.addSerialNumberToUsername(baseUsername, serialNumber);
        }

        log.debug("Resolved username after {} collisions: {}", serialNumber, candidate);
        return candidate;
    }
}