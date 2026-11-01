package com.giorgi.gymcrm.config;

import com.giorgi.gymcrm.web.RequestLoggingFilter;
import com.giorgi.gymcrm.web.TransactionIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    public static final int TRANSACTION_ID_ORDER = Ordered.HIGHEST_PRECEDENCE + 1;
    public static final int REQUEST_LOGGING_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;

    @Bean
    public FilterRegistrationBean<TransactionIdFilter> transactionIdFilter() {
        FilterRegistrationBean<TransactionIdFilter> registration =
                new FilterRegistrationBean<>(new TransactionIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(TRANSACTION_ID_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration =
                new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(REQUEST_LOGGING_ORDER);
        return registration;
    }
}
