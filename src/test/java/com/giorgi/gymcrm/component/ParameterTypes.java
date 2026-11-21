package com.giorgi.gymcrm.component;

import io.cucumber.java.DataTableType;
import io.cucumber.java.ParameterType;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

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

    @ParameterType("not found|unauthenticated|forbidden|invalid|locked")
    public HttpStatus rejection(String text) {
        return switch (text) {
            case "not found" -> HttpStatus.NOT_FOUND;
            case "unauthenticated" -> HttpStatus.UNAUTHORIZED;
            case "forbidden" -> HttpStatus.FORBIDDEN;
            case "locked" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    @DataTableType
    public Training training(Map<String, String> row) {
        return new Training(date(row.get("date")), Long.parseLong(row.get("minutes")));
    }

    public record Training(LocalDate date, long minutes) {
    }
}
