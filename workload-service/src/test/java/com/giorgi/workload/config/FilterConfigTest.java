package com.giorgi.workload.config;

import com.giorgi.workload.web.RequestLoggingFilter;
import com.giorgi.workload.web.TransactionIdFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.core.Ordered;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.filter.DelegatingFilterProxy;

import java.util.List;
import java.util.Set;

@SpringBootTest
@ActiveProfiles("test")
class FilterConfigTest {

    @Autowired
    private ListableBeanFactory beanFactory;

    @Autowired
    private FilterRegistrationBean<TransactionIdFilter> transactionIdFilter;

    @Autowired
    private FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter;

    private List<Filter> registeredFilters() {
        return new ServletContextInitializerBeans(beanFactory).stream()
                .filter(AbstractFilterRegistrationBean.class::isInstance)
                .<Filter>map(initializer -> ((AbstractFilterRegistrationBean<?>) initializer).getFilter())
                .toList();
    }

    private int positionOf(Class<? extends Filter> type) {
        List<Filter> filters = registeredFilters();
        for (int position = 0; position < filters.size(); position++) {
            if (type.isInstance(filters.get(position))) {
                return position;
            }
        }
        return Assertions.fail(type.getSimpleName() + " is not registered: " + filters);
    }

    @Test
    @DisplayName("both filters cover every path")
    void mapsBothFiltersToEveryPath() {
        Assertions.assertEquals(Set.of("/*"), Set.copyOf(transactionIdFilter.getUrlPatterns()));
        Assertions.assertEquals(Set.of("/*"), Set.copyOf(requestLoggingFilter.getUrlPatterns()));
    }

    @Test
    @DisplayName("the transaction id filter is ordered right before the request log")
    void ordersTransactionIdBeforeRequestLogging() {
        Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, transactionIdFilter.getOrder());
        Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, requestLoggingFilter.getOrder());
        Assertions.assertTrue(positionOf(TransactionIdFilter.class) < positionOf(RequestLoggingFilter.class),
                registeredFilters().toString());
    }

    @Test
    @DisplayName("both of our filters run before the spring security chain")
    void runsOurFiltersBeforeSecurity() {
        Assertions.assertTrue(positionOf(TransactionIdFilter.class) < positionOf(DelegatingFilterProxy.class),
                registeredFilters().toString());
        Assertions.assertTrue(positionOf(RequestLoggingFilter.class) < positionOf(DelegatingFilterProxy.class),
                registeredFilters().toString());
    }

    @Test
    @DisplayName("the security chain reaches the container as exactly one servlet filter")
    void registersTheSecurityChainOnce() {
        long occurrences = registeredFilters().stream()
                .filter(DelegatingFilterProxy.class::isInstance)
                .count();

        Assertions.assertEquals(1, occurrences, registeredFilters().toString());
    }
}
