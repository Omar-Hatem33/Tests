package com.team21.uber.driver.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Global exception handler.
 *
 * Without this, ResponseStatusException thrown by controllers (e.g. 404 NOT_FOUND)
 * can be intercepted by Spring Security's ExceptionTranslationFilter and converted
 * to 403 before reaching the client. Handling it here ensures the correct status
 * code is always returned directly from the application layer.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(
            ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }
}