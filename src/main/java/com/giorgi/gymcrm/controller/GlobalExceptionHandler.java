package com.giorgi.gymcrm.controller;

import com.giorgi.gymcrm.exception.AuthenticationException;
import com.giorgi.gymcrm.exception.ForbiddenOperationException;
import com.giorgi.gymcrm.exception.ProfileNotFoundException;
import com.giorgi.gymcrm.exception.StateConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String BASIC_CHALLENGE = "Basic realm=\"gym-crm\"";

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, BASIC_CHALLENGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage()));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail handleForbiddenOperation(ForbiddenOperationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ProblemDetail handleProfileNotFound(ProfileNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(StateConflictException.class)
    public ProblemDetail handleStateConflict(StateConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error while handling a request", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        body.setProperty("errors", errors);
        return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
    }
}
