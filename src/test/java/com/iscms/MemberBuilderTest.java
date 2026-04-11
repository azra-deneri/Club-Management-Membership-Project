package com.iscms;

import com.iscms.model.Member;
import com.iscms.model.MemberBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for MemberBuilder — validates all required field checks in build()
// No mocks needed — Builder pattern is pure Java with no external dependencies
public class MemberBuilderTest {

    // All required fields provided — build must succeed and return correct values
    @Test
    void build_validMember_success() {
        Member m = new MemberBuilder()
                .fullName("Ali Veli")
                .dateOfBirth(LocalDate.of(1995, 5, 10))
                .gender("MALE")
                .phone("5321234567")
                .email("ali@test.com")
                .password("hashed")
                .status("PENDING")
                .build();

        assertEquals("Ali Veli", m.getFullName());
        assertEquals("PENDING", m.getStatus());
    }

    // Missing full name — build must throw IllegalStateException
    @Test
    void build_missingName_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                new MemberBuilder()
                        .dateOfBirth(LocalDate.of(1995, 5, 10))
                        .phone("5321234567")
                        .build());
    }

    // Missing phone number — build must throw IllegalStateException
    @Test
    void build_missingPhone_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                new MemberBuilder()
                        .fullName("Ali Veli")
                        .dateOfBirth(LocalDate.of(1995, 5, 10))
                        .build());
    }

    // Missing date of birth — build must throw IllegalStateException
    @Test
    void build_missingDob_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                new MemberBuilder()
                        .fullName("Ali Veli")
                        .phone("5321234567")
                        .build());
    }
}