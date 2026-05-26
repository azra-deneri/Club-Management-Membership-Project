package com.iscms.exception;

// Thrown when a membership operation violates business rules:
// e.g. trying to freeze an already-frozen membership, cancel a pending one,
// upgrade tier while on PAYMENT_HOLD, etc.
// Distinct from IllegalArgumentException because it carries domain meaning
// — the user attempted a valid-looking action that the business policy forbids.
public class InvalidMembershipException extends RuntimeException {
    public InvalidMembershipException(String message) {
        super(message);
    }
}