package com.giorgi.gymcrm.config;

import com.giorgi.gymcrm.web.RequestLoggingFilter;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.util.List;
import java.util.Set;

@SpringBootTest
@AutoConfigureMockMvc
class FilterConfigTest {

    @Autowired
    private ListableBeanFactory beanFactory;

    @Autowired
    private FilterRegistrationBean<TransactionIdFilter> transactionIdFilter;

    @Autowired
    private FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter;

    // The filters in the order Boot hands them to the servlet container.
    private List<Filter> registeredFilters() {
        return new ServletContextInitializerBeans(beanFactory).stream()
                .filter(AbstractFilterRegistrationBean.class::isInstance)
                .<Filter>map(initializer -> ((AbstractFilterRegistrationBean<?>) initializer).getFilter())
                .toList();
    }

    @Test
    @DisplayName("both filters cover every path")
    void mapsBothFiltersToEveryPath() {
        Assertions.assertEquals(Set.of("/*"), Set.copyOf(transactionIdFilter.getUrlPatterns()));
        Assertions.assertEquals(Set.of("/*"), Set.copyOf(requestLoggingFilter.getUrlPatterns()));
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

    // Boot slots its own filters in between, so the positions matter only relative to each other.
    @Test
    @DisplayName("encoding runs first, then the transaction id, then the request log")
    void runsEncodingThenTransactionIdThenRequestLogging() {
        Assertions.assertEquals(0, positionOf(CharacterEncodingFilter.class), registeredFilters().toString());
        Assertions.assertTrue(positionOf(CharacterEncodingFilter.class) < positionOf(TransactionIdFilter.class),
                registeredFilters().toString());
        Assertions.assertTrue(positionOf(TransactionIdFilter.class) < positionOf(RequestLoggingFilter.class),
                registeredFilters().toString());
    }

    @Test
    @DisplayName("Boot's character encoding filter is registered")
    void keepsBootsEncodingFilter() {
        Assertions.assertTrue(registeredFilters().stream().anyMatch(CharacterEncodingFilter.class::isInstance),
                registeredFilters().toString());
    }

    @Test
    @DisplayName("our filters sit right behind the encoding filter")
    void ordersOurFiltersAfterEncoding() {
        Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, transactionIdFilter.getOrder());
        Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, requestLoggingFilter.getOrder());
    }
}
