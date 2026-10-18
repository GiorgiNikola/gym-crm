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

    @Test
    @DisplayName("the transaction id filter runs first and the request log right after it")
    void runsTransactionIdFirstThenRequestLogging() {
        List<Filter> filters = registeredFilters();

        Assertions.assertInstanceOf(TransactionIdFilter.class, filters.get(0), filters.toString());
        Assertions.assertInstanceOf(RequestLoggingFilter.class, filters.get(1), filters.toString());
    }

    @Test
    @DisplayName("Boot's character encoding filter is not registered")
    void leavesRequestEncodingToTheContainer() {
        Assertions.assertTrue(registeredFilters().stream().noneMatch(CharacterEncodingFilter.class::isInstance),
                registeredFilters().toString());
    }
}
