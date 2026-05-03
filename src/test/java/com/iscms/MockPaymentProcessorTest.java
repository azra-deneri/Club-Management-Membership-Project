package com.iscms;

import com.iscms.service.MockPaymentProcessor;
import com.iscms.service.PaymentResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for MockPaymentProcessor — verifies that each test card number
// produces the expected simulated outcome and that the expiry-date check
// fires before the card-number switch.
public class MockPaymentProcessorTest {

    private MockPaymentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MockPaymentProcessor();
    }

    // Future expiry date with the SUCCESS card → must succeed
    @Test
    void process_validSuccessCard_returnsSuccess() {
        YearMonth future = YearMonth.now().plusYears(2);
        PaymentResult result = processor.process(
                MockPaymentProcessor.CARD_SUCCESS, future);
        assertEquals(PaymentResult.SUCCESS, result);
    }

    // Insufficient-funds test card → must return INSUFFICIENT_FUNDS
    @Test
    void process_insufficientFundsCard_returnsInsufficientFunds() {
        YearMonth future = YearMonth.now().plusYears(2);
        PaymentResult result = processor.process(
                MockPaymentProcessor.CARD_INSUFFICIENT, future);
        assertEquals(PaymentResult.INSUFFICIENT_FUNDS, result);
    }

    // Expired test card with a valid future date → still must return EXPIRED_CARD
    // (the test card overrides the date check)
    @Test
    void process_expiredTestCardEvenWithFutureDate_returnsExpired() {
        YearMonth future = YearMonth.now().plusYears(2);
        PaymentResult result = processor.process(
                MockPaymentProcessor.CARD_EXPIRED, future);
        assertEquals(PaymentResult.EXPIRED_CARD, result);
    }

    // Bad-CVV test card → must return INVALID_CVV
    @Test
    void process_invalidCvvTestCard_returnsInvalidCvv() {
        YearMonth future = YearMonth.now().plusYears(2);
        PaymentResult result = processor.process(
                MockPaymentProcessor.CARD_INVALID_CVV, future);
        assertEquals(PaymentResult.INVALID_CVV, result);
    }

    // Past expiry date with the SUCCESS card → still must return EXPIRED_CARD
    // (date check runs before the card-number switch)
    @Test
    void process_pastExpiryWithSuccessCard_returnsExpired() {
        YearMonth past = YearMonth.now().minusMonths(1);
        PaymentResult result = processor.process(
                MockPaymentProcessor.CARD_SUCCESS, past);
        assertEquals(PaymentResult.EXPIRED_CARD, result);
    }

    // Random valid card number with future date → defaults to SUCCESS
    @Test
    void process_unknownCardWithFutureDate_returnsSuccess() {
        YearMonth future = YearMonth.now().plusYears(2);
        PaymentResult result = processor.process(
                "5555555555554444", future);
        assertEquals(PaymentResult.SUCCESS, result);
    }

    // Edge case: expiry exactly this month → not expired
    @Test
    void process_expiryThisMonth_isNotExpired() {
        YearMonth thisMonth = YearMonth.now();
        PaymentResult result = processor.process(
                MockPaymentProcessor.CARD_SUCCESS, thisMonth);
        assertEquals(PaymentResult.SUCCESS, result);
    }

    // describe() must return non-null user-visible text for every enum value.
    // Catches future enum additions where someone forgets to add a case.
    @Test
    void describe_allResults_returnNonEmptyMessage() {
        for (PaymentResult result : PaymentResult.values()) {
            String message = MockPaymentProcessor.describe(result);
            assertNotNull(message, "describe(" + result + ") returned null");
            assertFalse(message.isBlank(), "describe(" + result + ") returned blank");
        }
    }
}