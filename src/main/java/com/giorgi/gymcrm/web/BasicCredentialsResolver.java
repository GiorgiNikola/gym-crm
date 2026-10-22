package com.giorgi.gymcrm.web;

import com.giorgi.gymcrm.exception.AuthenticationException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BasicCredentialsResolver implements HandlerMethodArgumentResolver {

    private static final String BASIC_PREFIX = "Basic ";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Credentials.class.equals(parameter.getParameterType());
    }

    @Override
    public Credentials resolveArgument(MethodParameter parameter,
                                       ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest,
                                       WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BASIC_PREFIX)) {
            throw new AuthenticationException("Basic authentication is required");
        }

        String decoded = decode(header.substring(BASIC_PREFIX.length()));
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            throw new AuthenticationException("Basic credentials must be username:password");
        }

        Credentials credentials =
                new Credentials(decoded.substring(0, separator), decoded.substring(separator + 1));
        if (credentials.username().isBlank() || credentials.password().isBlank()) {
            throw new AuthenticationException("Basic credentials must carry a username and a password");
        }

        return credentials;
    }

    private String decode(String base64) {
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationException("Basic credentials are not valid base64");
        }
    }
}
