package com.iscms.service;

// Represents the result of a login attempt for any role (Member, Manager, Trainer)
// Uses a combination of static constants and factory methods
// Immutable — all fields are final, no setters
public class LoginResult {

    // All possible outcomes of a login attempt
    public enum Status {
        SUCCESS,             // Login successful
        NOT_FOUND,           // No account found with the given credentials
        WRONG_PASSWORD,      // Password incorrect — remaining attempts tracked
        LOCKED,              // Account locked due to too many failed attempts
        SUSPENDED,           // Member is suspended / Trainer is deactivated
        SUGGEST_RESET,       // Failed attempts reached the suggest-reset threshold
        PENDING,             // Member registration is awaiting manager approval
        REGISTRATION_FAILED, // Member registration was rejected by manager
        FROZEN,              // Member's membership is currently frozen
        PASSIVE              // Member's membership has expired — can still log in to renew
    }

    // The outcome status of this login attempt
    private final Status status;

    // The authenticated user object — Member, Manager, or Trainer (null if login failed)
    private final Object user;

    // Number of remaining attempts before account lockout (only relevant for WRONG_PASSWORD)
    private final int remainingTries;

    // Private constructor — all instances created via static constants or factory methods
    private LoginResult(Status status, Object user, int remainingTries) {
        this.status = status;
        this.user = user;
        this.remainingTries = remainingTries;
    }

    // --- Static constant instances for failure cases ---
    // Reusable singletons — no need to create new objects for each failure

    // No account found with the given phone/email/username
    public static final LoginResult NOT_FOUND           = new LoginResult(Status.NOT_FOUND, null, 0);

    // Account is locked — too many failed attempts
    public static final LoginResult LOCKED              = new LoginResult(Status.LOCKED, null, 0);

    // Member is suspended by manager / Trainer is deactivated
    public static final LoginResult SUSPENDED           = new LoginResult(Status.SUSPENDED, null, 0);

    // Reached the failed-attempt threshold where password reset is suggested
    public static final LoginResult SUGGEST_RESET       = new LoginResult(Status.SUGGEST_RESET, null, 0);

    // Member registration is pending manager approval
    public static final LoginResult PENDING             = new LoginResult(Status.PENDING, null, 0);

    // Member registration was rejected — account creation failed
    public static final LoginResult REGISTRATION_FAILED = new LoginResult(Status.REGISTRATION_FAILED, null, 0);

    // Member's membership is currently frozen
    public static final LoginResult FROZEN              = new LoginResult(Status.FROZEN, null, 0);

    // Member's membership has expired — login allowed to submit renewal request
    public static final LoginResult PASSIVE             = new LoginResult(Status.PASSIVE, null, 0);

    // --- Factory methods ---

    // Creates a SUCCESS result containing the authenticated user object
    public static LoginResult success(Object user) {
        return new LoginResult(Status.SUCCESS, user, 0);
    }

    // Creates a WRONG_PASSWORD result with the number of remaining attempts
    public static LoginResult wrong(int remaining) {
        return new LoginResult(Status.WRONG_PASSWORD, null, remaining);
    }

    // --- Getters ---

    // Returns true only if login was successful
    public boolean isSuccess()      { return status == Status.SUCCESS; }

    // Returns the status enum value of this result
    public Status getStatus()       { return status; }

    // Returns the authenticated user object (null if login failed)
    public Object getUser()         { return user; }

    // Returns remaining attempts before lockout (only meaningful for WRONG_PASSWORD)
    public int getRemainingTries()  { return remainingTries; }
}