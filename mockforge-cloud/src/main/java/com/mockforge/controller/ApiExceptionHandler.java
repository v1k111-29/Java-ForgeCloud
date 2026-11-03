package com.mockforge.controller;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import org.springframework.validation.FieldError;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    // Return 405 when the requested HTTP method is not supported.
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> method(Exception ex) {
        return ResponseEntity.status(405).body(Map.of("error", "Method not allowed"));
    }

    // Return 404 when a URL does not exist.
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<?> missing(Exception ex) {
        return ResponseEntity.status(404).body(Map.of("error", "Resource not found"));
    }

    // Return the HTTP error raised by a service.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason() == null ? "Request failed" : ex.getReason()));
    }

    // Collect validation errors so the caller knows which fields to fix.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException ex) {
        List<String> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(error.getField() + ": " + error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Validation failed", "details", errors));
    }

    @ExceptionHandler( {
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class
    })

    // Return 400 for malformed JSON or invalid parameter types.
    public ResponseEntity<?> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request format"));
    }

    // Return 409 when the method and path already exist in the workspace.
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> duplicate(Exception ex) {
        return ResponseEntity.status(409).body(Map.of("error", "Method and path already exist in this workspace"));
    }

    // Return 409 when a database relationship prevents the operation.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> integrity(Exception ex) {
        return ResponseEntity.status(409).body(Map.of("error", "Operation conflicts with related records"));
    }

    // Log unexpected failures and return a safe message with a reference ID.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ex) {
        String id = java.util.UUID.randomUUID().toString();
        LoggerFactory.getLogger(getClass()).error("Request failed, reference {}", id, ex);
        return ResponseEntity.status(500).body(Map.of("error", "Internal server error", "reference", id));
    }
}
