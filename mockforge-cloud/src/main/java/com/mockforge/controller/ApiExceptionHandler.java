package com.mockforge.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// @RestControllerAdvice = one central place that catches exceptions thrown by
// ANY controller and turns them into a clean JSON error, e.g. {"error": "..."}.
// Each @ExceptionHandler(X.class) method handles exception type X.
// ResponseEntity<?> = response with any body type ("?" = wildcard).
@RestControllerAdvice
public class ApiExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    // Validation can also happen in the service after workspaceId comes from the URL.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintValidationErrors(ConstraintViolationException exception) {

        List<String> validationErrors = new ArrayList<>();

        for (ConstraintViolation<?> validationError : exception.getConstraintViolations()) {
            validationErrors.add(validationError.getPropertyPath() + ": " + validationError.getMessage());
        }

        return ResponseEntity.badRequest().body(
            Map.of("error", "Validation failed", "details", validationErrors));
    }


    // The URL exists, but it does not support the HTTP method the caller used.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleUnsupportedHttpMethod(HttpRequestMethodNotSupportedException exception) {

        return ResponseEntity.status(405).body(Map.of("error", "Method not allowed"));
    }


    // No controller or static file matches the URL -> 404.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleMissingResource(NoResourceFoundException exception) {

        return ResponseEntity.status(404).body(Map.of("error", "Resource not found"));
    }


    // Keep the status and message chosen by the controller or service.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException exception) {

        String errorMessage = exception.getReason();

        if (errorMessage == null) {
            errorMessage = "Request failed";
        }

        return ResponseEntity.status(exception.getStatusCode())
            .body(Map.of("error", errorMessage));
    }


    // Return the field names as well as the messages, so the caller knows what to fix.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleRequestValidationErrors(MethodArgumentNotValidException exception) {

        List<String> validationErrors = new ArrayList<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            validationErrors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }

        return ResponseEntity.badRequest().body(
            Map.of("error", "Validation failed", "details", validationErrors));
    }


    // Broken JSON body, or a URL value of the wrong type (e.g. /api/endpoints/abc/logs
    // where a number was expected) -> 400.
    @ExceptionHandler({
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<?> handleMalformedRequest(Exception exception) {

        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request format"));
    }


    // A workspace cannot contain the same HTTP method and route twice.
    // The database UNIQUE rule throws DuplicateKeyException -> 409 Conflict.
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<?> handleDuplicateEndpoint(DuplicateKeyException exception) {

        return ResponseEntity.status(409)
            .body(Map.of("error", "Method and path already exist in this workspace"));
    }


    // Other database rule breaks (e.g. a foreign key pointing to a missing row) -> 409.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleRelatedRecordConflict(DataIntegrityViolationException exception) {

        return ResponseEntity.status(409)
            .body(Map.of("error", "Operation conflicts with related records"));
    }


    // Keep the failure details in the server log and give the caller a reference to it.
    // Catch-all for anything not handled above -> 500. The random UUID lets you find
    // the matching stack trace in the server log without showing it to the caller.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException(Exception exception) {

        String errorReference = UUID.randomUUID().toString();

        logger.error("Request failed, reference {}", errorReference, exception);

        return ResponseEntity.status(500)
            .body(Map.of("error", "Internal server error", "reference", errorReference));
    }
}
