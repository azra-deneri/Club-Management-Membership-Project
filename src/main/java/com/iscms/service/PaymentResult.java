package com.iscms.service;

// Outcome of a mock payment attempt — used by MockPaymentDialog and self-* service methods
// Distinguishes between user cancellation (no error to show) and various failure modes
// (each mapped to a specific message in the UI layer).
public enum PaymentResult {

    // Payment processed successfully
    SUCCESS,

    // User cancelled the payment dialog before submitting
    CANCELLED,

    // Card number didn't match a valid format (length / non-digit / etc.)
    INVALID_CARD,

    // CVV didn't match a valid format (must be 3 digits)
    INVALID_CVV,

    // Expiry date couldn't be parsed (e.g. month 14, garbled input)
    // Distinct from EXPIRED_CARD — this is a format problem, not a "card expired" decline
    INVALID_EXPIRY,

    // Expiration date is in the past (parsed correctly but past today)
    EXPIRED_CARD,

    // Card declined: insufficient funds (triggered by specific test card)
    INSUFFICIENT_FUNDS,

    // Card declined: generic processor failure (catch-all for the remaining test cases)
    DECLINED;

    // Returns true only for the SUCCESS outcome — convenient for callers
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}