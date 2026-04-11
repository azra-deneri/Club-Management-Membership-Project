package com.iscms;

import com.iscms.model.Member;
import com.iscms.service.MemberFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for MemberFactory — validates status assignment and field correctness
// No mocks needed — Factory and Builder are pure Java with no external dependencies
public class MemberFactoryTest {

    // createPendingMember must set status to PENDING
    @Test
    void createPendingMember_statusIsPending() {
        Member m = MemberFactory.createPendingMember(
                "Ali Veli", LocalDate.of(1995, 5, 10),
                "MALE", "5321234567", "ali@test.com", "password123");

        assertEquals("PENDING", m.getStatus());
    }

    // createPendingMember must correctly assign all provided field values
    @Test
    void createPendingMember_fieldsSetCorrectly() {
        Member m = MemberFactory.createPendingMember(
                "Ali Veli", LocalDate.of(1995, 5, 10),
                "MALE", "5321234567", "ali@test.com", "password123");

        assertEquals("Ali Veli", m.getFullName());
        assertEquals("5321234567", m.getPhone());
        assertEquals("ali@test.com", m.getEmail());
    }

    // createActiveMember must set status to ACTIVE
    @Test
    void createActiveMember_statusIsActive() {
        Member m = MemberFactory.createActiveMember(
                "Ayse Kaya", LocalDate.of(1990, 3, 15),
                "FEMALE", "5329876543", "ayse@test.com", "password123");

        assertEquals("ACTIVE", m.getStatus());
    }

    // createActiveMember must correctly assign all provided field values
    @Test
    void createActiveMember_fieldsSetCorrectly() {
        Member m = MemberFactory.createActiveMember(
                "Ayse Kaya", LocalDate.of(1990, 3, 15),
                "FEMALE", "5329876543", "ayse@test.com", "password123");

        assertEquals("Ayse Kaya", m.getFullName());
        assertEquals("FEMALE", m.getGender());
    }
}