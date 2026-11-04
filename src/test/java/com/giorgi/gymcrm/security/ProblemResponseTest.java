package com.giorgi.gymcrm.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

class ProblemResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemDetailWriter writer = new ProblemDetailWriter(objectMapper);
    private final ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(writer);
    private final ProblemAccessDeniedHandler accessDeniedHandler = new ProblemAccessDeniedHandler(writer);

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trainees/John.Smith");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private String bodyOf(MockHttpServletResponse written) throws Exception {
        return written.getContentAsString();
    }

    @Test
    @DisplayName("a request with no credentials gets a plain bearer challenge")
    void challengesAnAnonymousRequest() throws Exception {
        entryPoint.commence(request, response, new BadCredentialsException("Bad credentials"));

        Assertions.assertEquals(401, response.getStatus());
        Assertions.assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        Assertions.assertTrue(bodyOf(response).contains("\"detail\":\"Authentication is required\""), bodyOf(response));
    }

    @Test
    @DisplayName("a rejected token adds the invalid_token error code, as RFC 6750 asks")
    void challengesARejectedToken() throws Exception {
        entryPoint.commence(request, response, new InvalidTokenException("The bearer token is not valid"));

        Assertions.assertEquals(401, response.getStatus());
        Assertions.assertEquals("Bearer error=\"invalid_token\"", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        Assertions.assertTrue(bodyOf(response).contains("The bearer token is not valid"), bodyOf(response));
    }

    @Test
    @DisplayName("a 401 is problem json with the status and the path that was asked for")
    void writesAProblemDetailFor401() throws Exception {
        entryPoint.commence(request, response, new InvalidTokenException("nope"));

        Assertions.assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE),
                response.getContentType());
        String body = bodyOf(response);
        Assertions.assertTrue(body.contains("\"status\":401"), body);
        Assertions.assertTrue(body.contains("\"instance\":\"/trainees/John.Smith\""), body);
        Assertions.assertTrue(body.contains("\"title\":\"Unauthorized\""), body);
    }

    @Test
    @DisplayName("a 403 is the same problem json shape with the forbidden status")
    void writesAProblemDetailFor403() throws Exception {
        accessDeniedHandler.handle(request, response,
                new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)));

        Assertions.assertEquals(403, response.getStatus());
        Assertions.assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE),
                response.getContentType());
        String body = bodyOf(response);
        Assertions.assertTrue(body.contains("\"status\":403"), body);
        Assertions.assertTrue(body.contains("\"detail\":\"Access to this resource is denied\""), body);
    }

    @Test
    @DisplayName("a 403 carries no challenge header: the caller is known, just not allowed")
    void sendsNoChallengeOn403() throws Exception {
        accessDeniedHandler.handle(request, response,
                new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)));

        Assertions.assertNull(response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("the 403 handler works whether or not anybody is authenticated")
    void handles403WithAndWithoutAnAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("John.Smith", null, List.of()));
        accessDeniedHandler.handle(request, response,
                new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)));
        Assertions.assertEquals(403, response.getStatus());

        SecurityContextHolder.clearContext();
        MockHttpServletResponse anonymous = new MockHttpServletResponse();
        accessDeniedHandler.handle(request, anonymous,
                new AuthorizationDeniedException("Access Denied", new AuthorizationDecision(false)));
        Assertions.assertEquals(403, anonymous.getStatus());
    }
}
