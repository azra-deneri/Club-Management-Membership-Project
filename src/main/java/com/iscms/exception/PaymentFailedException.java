package com.iscms.exception;

// Thrown by the mock payment processor when a transaction is rejected
// (insufficient funds, expired card, invalid CVV). The message is safe to
// show to the user — it never contains card numbers or internal trace data.
public class PaymentFailedException extends RuntimeException {
    private final String reasonCode;

    public PaymentFailedException(String reasonCode, String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}