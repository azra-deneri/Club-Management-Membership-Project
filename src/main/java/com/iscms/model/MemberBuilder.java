package com.iscms.model;

import java.time.LocalDate;

// Builder pattern implementation for constructing Member objects
// Solves the problem of too many constructor parameters
// Allows readable, step-by-step object creation with centralized validation
public class MemberBuilder {

    // Internal Member instance being built — populated field by field
    private final Member member = new Member();

    // Sets the full name — returns 'this' to enable method chaining (fluent interface)
    public MemberBuilder fullName(String fullName) {
        member.setFullName(fullName); return this;
    }

    // Sets the date of birth — used later for age validation (BR-01: must be 18+)
    public MemberBuilder dateOfBirth(LocalDate dob) {
        member.setDateOfBirth(dob); return this;
    }

    // Sets the gender (e.g., MALE, FEMALE)
    public MemberBuilder gender(String gender) {
        member.setGender(gender); return this;
    }

    // Sets the unique phone number
    public MemberBuilder phone(String phone) {
        member.setPhone(phone); return this;
    }

    // Sets the unique email address
    public MemberBuilder email(String email) {
        member.setEmail(email); return this;
    }

    // Sets the BCrypt-hashed password
    public MemberBuilder password(String password) {
        member.setPassword(password); return this;
    }

    // Sets the initial account status (e.g., PENDING, ACTIVE)
    public MemberBuilder status(String status) {
        member.setStatus(status); return this;
    }

    // Sets the body weight in kg — optional field (nullable)
    public MemberBuilder weight(Double weight) {
        member.setWeight(weight); return this;
    }

    // Sets the height in cm — optional field (nullable)
    public MemberBuilder height(Double height) {
        member.setHeight(height); return this;
    }

    // Validates all required fields and returns the fully constructed Member object
    // Validation is centralized here — not scattered across the codebase
    public Member build() {
        // Full name is required and must not be blank
        if (member.getFullName() == null || member.getFullName().isBlank())
            throw new IllegalStateException("Full name is required.");

        // Phone number is required and must not be blank
        if (member.getPhone() == null || member.getPhone().isBlank())
            throw new IllegalStateException("Phone is required.");

        // Date of birth is required (used for 18+ age check in service layer)
        if (member.getDateOfBirth() == null)
            throw new IllegalStateException("Date of birth is required.");

        // All validations passed — return the constructed Member object
        return member;
    }
}