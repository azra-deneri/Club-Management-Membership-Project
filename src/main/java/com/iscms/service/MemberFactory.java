package com.iscms.service;

import com.iscms.model.Member;
import com.iscms.model.MemberBuilder;

import java.time.LocalDate;

// Factory class responsible for creating Member objects in different initial states
// Implements the Factory pattern — centralizes object creation logic per use case
// Combines with Builder pattern: MemberFactory calls MemberBuilder internally
// Validation (age, phone, name requirements) is enforced by MemberBuilder.build()
public class MemberFactory {

    // Creates a Member with PENDING status
    // Used when a member self-registers through the registration form (UC-M02)
    // PENDING means the account is waiting for manager approval before activation
    public static Member createPendingMember(String fullName, LocalDate dob,
                                             String gender, String phone,
                                             String email, String password) {
        return new MemberBuilder()
                .fullName(fullName)
                .dateOfBirth(dob)
                .gender(gender)
                .phone(phone)
                .email(email)
                .password(password)
                .status("PENDING")
                .build(); // Validation runs here — throws IllegalStateException if invalid
    }

    // Creates a Member with ACTIVE status
    // Used when a manager adds a member directly without approval flow (UC-A02)
    // ACTIVE means the account is immediately usable — no approval step needed
    public static Member createActiveMember(String fullName, LocalDate dob,
                                            String gender, String phone,
                                            String email, String password) {
        return new MemberBuilder()
                .fullName(fullName)
                .dateOfBirth(dob)
                .gender(gender)
                .phone(phone)
                .email(email)
                .password(password)
                .status("ACTIVE")
                .build(); // Validation runs here — throws IllegalStateException if invalid
    }
}