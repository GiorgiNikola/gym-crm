package com.giorgi.workload.component;

import com.giorgi.workload.messaging.DeadLetterReason;
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

    @ParameterType("unauthenticated|forbidden|malformed|invalid")
    public DeadLetterReason deadLetterReason(String text) {
        return DeadLetterReason.valueOf(text.toUpperCase(Locale.ROOT));
    }

    @ParameterType("not found|unauthenticated|forbidden|invalid")
    public HttpStatus rejection(String text) {
        return switch (text) {
            case "not found" -> HttpStatus.NOT_FOUND;
            case "unauthenticated" -> HttpStatus.UNAUTHORIZED;
            case "forbidden" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    @ParameterType("monthly workload|workload summary")
    public WorkloadView view(String text) {
        return text.equals("monthly workload") ? WorkloadView.MONTHLY : WorkloadView.SUMMARY;
    }

    @DataTableType
    public MonthTotal monthTotal(Map<String, String> row) {
        return new MonthTotal(month(row.get("month")), Long.parseLong(row.get("minutes")));
    }

    @DataTableType
    public Training training(Map<String, String> row) {
        return new Training(date(row.get("date")), Integer.parseInt(row.get("minutes")));
    }

    public record MonthTotal(YearMonth month, long minutes) {
    }

    public record Training(LocalDate date, int minutes) {
    }

    public enum WorkloadView {
        MONTHLY,
        SUMMARY
    }
}
