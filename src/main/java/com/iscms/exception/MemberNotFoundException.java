package com.iscms.exception;

// Specific subtype for missing members — lets the GlobalExceptionHandler
// distinguish "member not found" from a generic "resource not found"
// when we want a tailored message or HTTP status.
public class MemberNotFoundException extends ResourceNotFoundException {
    public MemberNotFoundException(int memberId) {
        super("Member not found (id=" + memberId + ").");
    }

    public MemberNotFoundException(String message) {
        super(message);
    }
}