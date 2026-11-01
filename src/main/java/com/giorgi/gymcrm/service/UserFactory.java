package com.giorgi.gymcrm.service;

import com.giorgi.gymcrm.model.User;
import com.giorgi.gymcrm.util.CredentialGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserFactory {

    private final UsernameResolver usernameResolver;
    private final CredentialGenerator credentialGenerator;
    private final PasswordEncoder passwordEncoder;

    public UserFactory(UsernameResolver usernameResolver,
                       CredentialGenerator credentialGenerator,
                       PasswordEncoder passwordEncoder) {
        this.usernameResolver = usernameResolver;
        this.credentialGenerator = credentialGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    public NewUser create(String firstName, String lastName) {
        String rawPassword = credentialGenerator.generatePassword();

        User user = User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .username(usernameResolver.generateUsername(firstName, lastName))
                .password(passwordEncoder.encode(rawPassword))
                .isActive(true)
                .build();

        return new NewUser(user, rawPassword);
    }
}
