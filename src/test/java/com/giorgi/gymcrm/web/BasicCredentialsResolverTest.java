package com.giorgi.gymcrm.web;

import com.giorgi.gymcrm.exception.AuthenticationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

class BasicCredentialsResolverTest {

    private final BasicCredentialsResolver resolver = new BasicCredentialsResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private Credentials resolve() {
        return resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    private void authorization(String value) {
        request.addHeader("Authorization", value);
    }

    private void basic(String usernameAndPassword) {
        authorization("Basic " + Base64.getEncoder()
                .encodeToString(usernameAndPassword.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("a valid header becomes a username and a password")
    void readsValidHeader() {
        basic("John.Smith:aX7kQ2mN9p");

        Credentials credentials = resolve();

        Assertions.assertEquals("John.Smith", credentials.username());
        Assertions.assertEquals("aX7kQ2mN9p", credentials.password());
    }

    @Test
    @DisplayName("only the first colon separates, so a password may contain colons")
    void keepsColonsInPassword() {
        basic("John.Smith:pass:with:colons");

        Credentials credentials = resolve();

        Assertions.assertEquals("John.Smith", credentials.username());
        Assertions.assertEquals("pass:with:colons", credentials.password());
    }

    @Test
    @DisplayName("a request without the header is rejected")
    void rejectsMissingHeader() {
        Assertions.assertThrows(AuthenticationException.class, this::resolve);
    }

    @Test
    @DisplayName("a scheme other than Basic is rejected")
    void rejectsOtherScheme() {
        authorization("Bearer sometoken");

        Assertions.assertThrows(AuthenticationException.class, this::resolve);
    }

    @Test
    @DisplayName("a header that is not base64 is rejected")
    void rejectsBadBase64() {
        authorization("Basic not base64!!");

        Assertions.assertThrows(AuthenticationException.class, this::resolve);
    }

    @Test
    @DisplayName("a header without a colon is rejected")
    void rejectsMissingColon() {
        basic("John.Smith");

        Assertions.assertThrows(AuthenticationException.class, this::resolve);
    }

    @Test
    @DisplayName("a blank username is failed authentication, not a validation error")
    void rejectsBlankUsername() {
        basic("   :aX7kQ2mN9p");

        Assertions.assertThrows(AuthenticationException.class, this::resolve);
    }

    @Test
    @DisplayName("a blank password is failed authentication too")
    void rejectsBlankPassword() {
        basic("John.Smith:   ");

        Assertions.assertThrows(AuthenticationException.class, this::resolve);
    }
}
