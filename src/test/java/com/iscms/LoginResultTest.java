package com.iscms;

import com.iscms.service.LoginResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for LoginResult — verifies all status constants and factory methods
// No mocks needed — LoginResult is a pure Java value object
public class LoginResultTest {

    // success() must return SUCCESS status with the user object and 0 remaining tries
    @Test
    void success_returnsSuccessStatus_withUser() {
        Object user = new Object();
        LoginResult result = LoginResult.success(user);

        assertEquals(LoginResult.Status.SUCCESS, result.getStatus());
        assertSame(user, result.getUser());
        assertEquals(0, result.getRemainingTries());
    }

    // wrong() must return WRONG_PASSWORD status with the correct remaining tries count
    @Test
    void wrong_returnsWrongPasswordStatus_withRemainingTries() {
        LoginResult result = LoginResult.wrong(3);

        assertEquals(LoginResult.Status.WRONG_PASSWORD, result.getStatus());
        assertEquals(3, result.getRemainingTries());
        assertNull(result.getUser());
    }

    // LOCKED constant must have LOCKED status and null user
    @Test
    void locked_returnsLockedStatus() {
        assertEquals(LoginResult.Status.LOCKED, LoginResult.LOCKED.getStatus());
        assertNull(LoginResult.LOCKED.getUser());
    }

    // NOT_FOUND constant must have NOT_FOUND status
    @Test
    void notFound_returnsNotFoundStatus() {
        assertEquals(LoginResult.Status.NOT_FOUND, LoginResult.NOT_FOUND.getStatus());
    }

    // SUSPENDED constant must have SUSPENDED status
    @Test
    void suspended_returnsSuspendedStatus() {
        assertEquals(LoginResult.Status.SUSPENDED, LoginResult.SUSPENDED.getStatus());
    }

    // PENDING constant must have PENDING status
    @Test
    void pending_returnsPendingStatus() {
        assertEquals(LoginResult.Status.PENDING, LoginResult.PENDING.getStatus());
    }

    // FROZEN constant must have FROZEN status
    @Test
    void frozen_returnsFrozenStatus() {
        assertEquals(LoginResult.Status.FROZEN, LoginResult.FROZEN.getStatus());
    }

    // SUGGEST_RESET constant must have SUGGEST_RESET status
    @Test
    void suggestReset_returnsSuggestResetStatus() {
        assertEquals(LoginResult.Status.SUGGEST_RESET, LoginResult.SUGGEST_RESET.getStatus());
    }

    // REGISTRATION_FAILED constant must have REGISTRATION_FAILED status
    @Test
    void registrationFailed_returnsCorrectStatus() {
        assertEquals(LoginResult.Status.REGISTRATION_FAILED,
                LoginResult.REGISTRATION_FAILED.getStatus());
    }

    // PASSIVE constant must have PASSIVE status — member with expired membership
    @Test
    void passive_returnsPassiveStatus() {
        assertEquals(LoginResult.Status.PASSIVE, LoginResult.PASSIVE.getStatus());
        assertNull(LoginResult.PASSIVE.getUser());
    }
}