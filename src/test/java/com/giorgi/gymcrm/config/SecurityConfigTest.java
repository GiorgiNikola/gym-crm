package com.giorgi.gymcrm.config;

import com.giorgi.gymcrm.security.JwtAuthenticationFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@SpringBootTest
class SecurityConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private List<Filter> securityFilters() {
        List<SecurityFilterChain> chains = securityFilterChain.getFilterChains();
        Assertions.assertEquals(1, chains.size(), "expected a single security filter chain");
        return chains.get(0).getFilters();
    }

    private CorsConfiguration cors() {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/trainees/John.Smith");
        return corsConfigurationSource.getCorsConfiguration(request);
    }

    @Test
    @DisplayName("passwords are hashed with BCrypt")
    void usesBcrypt() {
        Assertions.assertInstanceOf(BCryptPasswordEncoder.class, passwordEncoder);
    }

    @Test
    @DisplayName("the encoder salts, so the same password never produces the same hash")
    void saltsEveryHash() {
        String first = passwordEncoder.encode("aX7kQ2mN9p");
        String second = passwordEncoder.encode("aX7kQ2mN9p");

        Assertions.assertNotEquals(first, second);
        Assertions.assertTrue(passwordEncoder.matches("aX7kQ2mN9p", first));
        Assertions.assertTrue(passwordEncoder.matches("aX7kQ2mN9p", second));
    }

    @Test
    @DisplayName("an authentication manager is wired, so login never compares strings by hand")
    void exposesAnAuthenticationManager() {
        Assertions.assertNotNull(authenticationManager);
    }

    @Test
    @DisplayName("the jwt filter is not a bean, so Boot cannot register it as a servlet filter too")
    void keepsTheJwtFilterOutOfTheBeanFactory() {
        Assertions.assertTrue(
                applicationContext.getBeansOfType(JwtAuthenticationFilter.class).isEmpty(),
                applicationContext.getBeansOfType(JwtAuthenticationFilter.class).keySet().toString());
    }

    @Test
    @DisplayName("the jwt filter sits exactly once in the security chain")
    void addsTheJwtFilterOnce() {
        long occurrences = securityFilters().stream()
                .filter(JwtAuthenticationFilter.class::isInstance)
                .count();

        Assertions.assertEquals(1, occurrences, securityFilters().toString());
    }

    @Test
    @DisplayName("the jwt filter runs before the logout filter, so the logout handler sees the user")
    void placesTheJwtFilterBeforeLogout() {
        List<Filter> filters = securityFilters();
        int jwt = indexOf(filters, JwtAuthenticationFilter.class);
        int logout = indexOf(filters, LogoutFilter.class);

        Assertions.assertTrue(jwt >= 0, filters.toString());
        Assertions.assertTrue(logout >= 0, filters.toString());
        Assertions.assertTrue(jwt < logout, filters.toString());
    }

    @Test
    @DisplayName("logout is handled by the security chain rather than by a controller")
    void keepsLogoutInTheChain() {
        Assertions.assertTrue(securityFilters().stream().anyMatch(LogoutFilter.class::isInstance),
                securityFilters().toString());
    }

    @Test
    @DisplayName("the filter order of the chain is stable")
    void listsTheFilterOrder() {
        List<String> names = securityFilters().stream()
                .map(filter -> filter.getClass().getSimpleName())
                .toList();

        Assertions.assertEquals(List.of(
                "DisableEncodeUrlFilter",
                "WebAsyncManagerIntegrationFilter",
                "SecurityContextHolderFilter",
                "HeaderWriterFilter",
                "CorsFilter",
                "JwtAuthenticationFilter",
                "LogoutFilter",
                "RequestCacheAwareFilter",
                "SecurityContextHolderAwareRequestFilter",
                "AnonymousAuthenticationFilter",
                "SessionManagementFilter",
                "ExceptionTranslationFilter",
                "AuthorizationFilter"), names);
    }

    private static int indexOf(List<Filter> filters, Class<? extends Filter> type) {
        for (int position = 0; position < filters.size(); position++) {
            if (type.isInstance(filters.get(position))) {
                return position;
            }
        }
        return -1;
    }

    @Test
    @DisplayName("neither basic auth nor a login form is left in the chain")
    void leavesOutTheBrowserLogins() {
        List<String> names = securityFilters().stream().map(filter -> filter.getClass().getSimpleName()).toList();

        Assertions.assertFalse(names.contains("BasicAuthenticationFilter"), names.toString());
        Assertions.assertFalse(names.contains("UsernamePasswordAuthenticationFilter"), names.toString());
    }

    @Test
    @DisplayName("csrf is off, because the credential is a header a foreign page cannot add")
    void leavesOutTheCsrfFilter() {
        List<String> names = securityFilters().stream().map(filter -> filter.getClass().getSimpleName()).toList();

        Assertions.assertFalse(names.contains("CsrfFilter"), names.toString());
    }

    @Test
    @DisplayName("cors is handled inside the security chain")
    void keepsCorsInTheChain() {
        List<String> names = securityFilters().stream().map(filter -> filter.getClass().getSimpleName()).toList();

        Assertions.assertTrue(names.contains("CorsFilter"), names.toString());
    }

    @Test
    @DisplayName("the allowed origins are the ones the properties list")
    void allowsTheConfiguredOrigins() {
        Assertions.assertEquals(List.of("http://localhost:3000", "http://localhost:4200"),
                cors().getAllowedOrigins());
    }

    @Test
    @DisplayName("every method the api uses is allowed, preflight included")
    void allowsEveryMethodTheApiUses() {
        Assertions.assertEquals(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
                cors().getAllowedMethods());
    }

    @Test
    @DisplayName("the token, the content type and the transaction id may be sent")
    void allowsTheHeadersTheApiNeeds() {
        Assertions.assertEquals(List.of("Authorization", "Content-Type", "X-Transaction-Id"),
                cors().getAllowedHeaders());
    }

    @Test
    @DisplayName("the transaction id and the Location come back readable to a browser")
    void exposesTheHeadersAClientNeedsToRead() {
        Assertions.assertEquals(List.of("X-Transaction-Id", "Location"), cors().getExposedHeaders());
    }

    @Test
    @DisplayName("credentials are not allowed, because there is no cookie to send")
    void sendsNoCookies() {
        Assertions.assertNotEquals(Boolean.TRUE, cors().getAllowCredentials());
    }
}
