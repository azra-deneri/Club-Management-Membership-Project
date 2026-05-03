package com.iscms.service;

import java.time.YearMonth;

// Mock payment processor — simulates the behaviour of a real payment gateway
// (Stripe, Iyzico, etc.) without making any external calls. In production this
// would be replaced by an HTTP call to the gateway's API.
//
// Test card numbers mirror Stripe's conventions for a familiar demo:
//   4242 4242 4242 4242 → SUCCESS
//   4000 0000 0000 0002 → INSUFFICIENT_FUNDS
//   4000 0000 0000 0069 → EXPIRED_CARD (forced even with a valid expiry)
//   4000 0000 0000 0127 → INVALID_CVV (forced even with a valid CVV format)
//   any other valid format → SUCCESS
//
// Note: card data is never persisted. The processor receives plain values,
// computes a result, and discards the inputs. PCI-DSS compliant by design —
// no card information ever reaches the database or logs.
public class MockPaymentProcessor {

    // Public test card constants — exposed so the dialog can show them as a hint
    // and so unit tests can reference them without duplicating string literals.
    public static final String CARD_SUCCESS         = "4242424242424242";
    public static final String CARD_INSUFFICIENT    = "4000000000000002";
    public static final String CARD_EXPIRED         = "4000000000000069";
    public static final String CARD_INVALID_CVV     = "4000000000000127";

    // Processes a payment with already-validated inputs.
    // Caller (MockPaymentDialog) is responsible for format validation —
    // by the time we reach this method, cardDigits is guaranteed to be 16
    // digits and expiryMonth is non-null and parseable.
    //
    // The order of checks here mirrors how a real processor would respond:
    //   1. Card expiry first — declined before any balance check
    //   2. Specific test cards (override real-world simulation)
    //   3. Default to success for any other valid card
    public PaymentResult process(String cardDigits, YearMonth expiryMonth) {
        // Card declined: expired (date in the past)
        if (expiryMonth.isBefore(YearMonth.now())) {
            return PaymentResult.EXPIRED_CARD;
        }

        // Specific test cards trigger specific failure modes
        return switch (cardDigits) {
            case CARD_INSUFFICIENT -> PaymentResult.INSUFFICIENT_FUNDS;
            case CARD_EXPIRED      -> PaymentResult.EXPIRED_CARD;
            case CARD_INVALID_CVV  -> PaymentResult.INVALID_CVV;
            default                -> PaymentResult.SUCCESS;
        };
    }

    // Maps a PaymentResult to a user-visible explanation. Centralised here
    // so that callers don't duplicate the same switch statement when they
    // need to display the failure reason.
    public static String describe(PaymentResult result) {
        return switch (result) {
            case SUCCESS            -> "Payment successful.";
            case CANCELLED          -> "Payment cancelled.";
            case INVALID_CARD       -> "Invalid card number. Please check and try again.";
            case INVALID_CVV        -> "Invalid CVV. Please check the 3-digit code on the back of your card.";
            case INVALID_EXPIRY     -> "Invalid expiry date.";
            case EXPIRED_CARD       -> "Your card has expired. Please use a different card.";
            case INSUFFICIENT_FUNDS -> "Insufficient funds. Your card was declined.";
            case DECLINED           -> "Your card was declined. Please try a different card.";
        };
    }
}