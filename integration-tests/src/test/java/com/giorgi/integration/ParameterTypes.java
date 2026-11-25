package com.giorgi.integration;

import io.cucumber.java.ParameterType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ParameterTypes {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    @ParameterType("\\d{1,2} [A-Z][a-z]+ \\d{4}")
    public LocalDate date(String text) {
        return LocalDate.parse(text, DAY);
    }

    @ParameterType("[A-Z][a-z]+ \\d{4}")
    public YearMonth month(String text) {
        return YearMonth.parse(text, MONTH);
    }

    @ParameterType("trainee|trainer")
    public String role(String text) {
        return text;
    }
}
