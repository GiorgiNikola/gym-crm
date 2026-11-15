package com.giorgi.gymcrm.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LogoutHandlerTest {

    private static final TokenClaims CLAIMS =
            new TokenClaims("John.Smith", "token-id", Instant.parse("2026-08-01T11:00:00Z"));

    @Mock
    private TokenRevocationService revocations;

    private final ProblemDetailWriter writer = new ProblemDetailWriter(new ObjectMapper());
    private final LogoutResponseHandler responseHandler =
            new LogoutResponseHandler(new ProblemAuthenticationEntryPoint(writer));

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/logout");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private TokenRevokingLogoutHandler revokingHandler() {
        return new TokenRevokingLogoutHandler(revocations);
    }

    @Test
    @DisplayName("the token the caller presented is revoked")
    void revokesTheCurrentToken() {
        revokingHandler().logout(request, response, new JwtAuthenticationToken(CLAIMS));

        verify(revocations).revoke(CLAIMS);
    }

    @Test
    @DisplayName("there is nothing to revoke when nobody was authenticated")
    void revokesNothingWithoutAnAuthentication() {
        revokingHandler().logout(request, response, null);

        verifyNoInteractions(revocations);
    }

    @Test
    @DisplayName("an authentication of another kind carries no token to revoke")
    void revokesNothingForAnotherAuthenticationType() {
        revokingHandler().logout(request, response,
                UsernamePasswordAuthenticationToken.authenticated("John.Smith", null, List.of()));

        verifyNoInteractions(revocations);
    }

    @Test
    @DisplayName("a logged out caller gets 204 with an empty body")
    void answers204() throws Exception {
        responseHandler.onLogoutSuccess(request, response, new JwtAuthenticationToken(CLAIMS));

        Assertions.assertEquals(204, response.getStatus());
        Assertions.assertEquals("", response.getContentAsString());
    }

    @Test
    @DisplayName("a logout with no token gets the same 401 problem detail as any other endpoint")
    void answers401WithoutAToken() throws Exception {
        responseHandler.onLogoutSuccess(request, response, null);

        Assertions.assertEquals(401, response.getStatus());
        Assertions.assertEquals("Bearer", response.getHeader(HttpHeaders.WWW_AUTHENTICATE));
        Assertions.assertTrue(response.getContentAsString().contains("\"status\":401"),
                response.getContentAsString());
    }
}
