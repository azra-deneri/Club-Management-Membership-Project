package com.iscms.exception;

// Raised when a request lacks the required role or session.
// Most session checks redirect to /login directly, but for cases where a
// role mismatch is detected mid-flow (e.g. a MEMBER hitting a /manager URL
// after their session was tampered with), this exception lets the global
// handler render a 403-style page rather than a silent redirect.
public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}