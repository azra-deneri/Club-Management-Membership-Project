package com.iscms.exception;

// Base exception for "entity does not exist" scenarios across the application.
// Why RuntimeException: Spring's @ExceptionHandler works seamlessly with unchecked
// exceptions, and these conditions are programmer-visible (caller should know
// to handle missing data), not recoverable I/O errors.
// Used by services when a lookup by ID returns Optional.empty().
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}