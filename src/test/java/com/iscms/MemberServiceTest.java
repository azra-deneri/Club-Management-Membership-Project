package com.iscms;

import com.iscms.dao.*;
import com.iscms.model.*;
import com.iscms.service.MemberService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

// Unit tests for MemberService — covers registration, freeze, upgrade, BMI, and request expiry
// Uses Mockito to mock DAO interfaces — no real DB calls
@ExtendWith(MockitoExtension.class)
public class MemberServiceTest {

    // Mocked DAO interfaces — injected into MemberService via constructor
    @Mock private MemberDAO memberDAO;
    @Mock private MembershipDAO membershipDAO;
    @Mock private RequestDAO requestDAO;
    @Mock private PaymentDAO paymentDAO;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberDAO, membershipDAO, requestDAO, paymentDAO);
    }

    // --- Registration Tests ---

    // Member must be at least 18 years old to register
    @Test
    void createRegistrationRequest_under18_throwsException() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(17));
        assertThrows(IllegalArgumentException.class,
                () -> memberService.createRegistrationRequest(m, "CLASSIC", "MONTHLY"));
    }

    // Exactly 18 years old is allowed — boundary value test
    @Test
    void createRegistrationRequest_exactly18_success() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(18));
        when(memberDAO.existsByPhone("5321234567")).thenReturn(false);
        when(memberDAO.existsByEmail("test@test.com")).thenReturn(false);
        when(memberDAO.findByPhone("5321234567")).thenReturn(Optional.of(m));
        assertDoesNotThrow(() ->
                memberService.createRegistrationRequest(m, "CLASSIC", "MONTHLY"));
    }

    // Phone number must be unique across all members
    @Test
    void createRegistrationRequest_duplicatePhone_throwsException() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.existsByPhone("5321234567")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> memberService.createRegistrationRequest(m, "CLASSIC", "MONTHLY"));
    }

    // Email address must be unique across all members
    @Test
    void createRegistrationRequest_duplicateEmail_throwsException() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.existsByPhone("5321234567")).thenReturn(false);
        when(memberDAO.existsByEmail("test@test.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> memberService.createRegistrationRequest(m, "CLASSIC", "MONTHLY"));
    }

    // --- Pricing Tests ---

    // CLASSIC monthly fee must be 750 TL
    @Test
    void calculateAmount_monthly_classic_returns750() {
        assertEquals(750.0, memberService.calculateAmount("CLASSIC", "MONTHLY"));
    }

    // GOLD monthly fee must be 1250 TL
    @Test
    void calculateAmount_monthly_gold_returns1250() {
        assertEquals(1250.0, memberService.calculateAmount("GOLD", "MONTHLY"));
    }

    // VIP monthly fee must be 2000 TL
    @Test
    void calculateAmount_monthly_vip_returns2000() {
        assertEquals(2000.0, memberService.calculateAmount("VIP", "MONTHLY"));
    }

    // Annual prepaid applies 15% discount on 12 months total
    @Test
    void calculateAmount_annualPrepaid_classic_applies15PercentDiscount() {
        double expected = 750.0 * 12 * 0.85;
        assertEquals(expected, memberService.calculateAmount("CLASSIC", "ANNUAL_PREPAID"));
    }

    // Annual installment applies 7% surcharge on monthly price
    @Test
    void calculateAmount_annualInstallment_classic_applies7PercentSurcharge() {
        double expected = 750.0 * 1.07;
        assertEquals(expected,
                memberService.calculateAmount("CLASSIC", "ANNUAL_INSTALLMENT"), 0.01);
    }

    // Annual prepaid VIP also applies 15% discount
    @Test
    void calculateAmount_annualPrepaid_vip_applies15PercentDiscount() {
        double expected = 2000.0 * 12 * 0.85;
        assertEquals(expected, memberService.calculateAmount("VIP", "ANNUAL_PREPAID"));
    }

    // --- Freeze Tests ---

    // CLASSIC tier allows only 1 freeze — exceeding it must throw
    @Test
    void freezeMembership_classicExceedsLimit_throwsException() {
        Membership ms = buildMembership(1, 1, "CLASSIC", 1,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

    // GOLD tier allows only 2 freezes — exceeding it must throw
    @Test
    void freezeMembership_goldExceedsLimit_throwsException() {
        Membership ms = buildMembership(1, 1, "GOLD", 2,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

    // VIP tier allows only 3 freezes — exceeding it must throw
    @Test
    void freezeMembership_vipExceedsLimit_throwsException() {
        Membership ms = buildMembership(1, 1, "VIP", 3,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

    // Cannot freeze within 3 days of membership expiry
    @Test
    void freezeMembership_within3DaysOfExpiry_throwsException() {
        Membership ms = buildMembership(1, 1, "VIP", 0,
                LocalDate.now().plusDays(2)); // only 2 days left
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

    // Unfreeze must set both membership and member status back to ACTIVE
    @Test
    void unfreezeMembership_setsActiveStatus() {
        Membership ms = buildMembership(1, 42, "GOLD", 1,
                LocalDate.now().plusDays(20));
        ms.setStatus("FROZEN");
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));

        memberService.unfreezeMembership(1);

        verify(membershipDAO, times(1)).updateStatus(1, "ACTIVE");
        verify(memberDAO, times(1)).updateStatus(42, "ACTIVE");
    }

    // --- Tier Upgrade Tests ---

    // No active membership — tier upgrade request must be blocked
    @Test
    void createTierUpgradeRequest_noActiveMembership_throwsException() {
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> memberService.createTierUpgradeRequest(
                        1, 1, "CLASSIC", "GOLD", "MONTHLY", 500.0));
    }

    // Valid upgrade — must call requestDAO.insertTierUpgrade()
    @Test
    void createTierUpgradeRequest_validUpgrade_callsInsert() {
        Membership ms = buildMembership(1, 1, "CLASSIC", 0,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));

        memberService.createTierUpgradeRequest(1, 1, "CLASSIC", "GOLD", "MONTHLY", 500.0);

        verify(requestDAO).insertTierUpgrade(any());
    }

    // --- Phone Update Tests ---

    // Same phone number — must not throw (member keeps their own number)
    @Test
    void updatePhone_samePhone_doesNotThrow() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.findById(1)).thenReturn(Optional.of(m));
        assertDoesNotThrow(() -> memberService.updatePhone(1, "5321234567"));
    }

    // New phone already taken by another member — must throw
    @Test
    void updatePhone_differentDuplicatePhone_throwsException() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.findById(1)).thenReturn(Optional.of(m));
        when(memberDAO.existsByPhone("5399999999")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> memberService.updatePhone(1, "5399999999"));
    }

    // Unique new phone — must call DAO updatePhone
    @Test
    void updatePhone_uniqueNewPhone_callsDAO() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.findById(1)).thenReturn(Optional.of(m));
        when(memberDAO.existsByPhone("5399999999")).thenReturn(false);

        memberService.updatePhone(1, "5399999999");

        verify(memberDAO).updatePhone(1, "5399999999");
    }

    // --- Registration Approval / Rejection Tests ---

    // Rejecting an INITIAL request — must delete both request and member record
    @Test
    void rejectRegistration_initial_deletesMember() {
        RegistrationRequest req = new RegistrationRequest();
        req.setRequestId(1);
        req.setMemberId(42);
        req.setType("INITIAL");
        when(requestDAO.findPendingRegistrations())
                .thenReturn(new ArrayList<>(List.of(req)));

        memberService.rejectRegistration(1);

        verify(requestDAO, times(1)).deleteRegistration(1);
        verify(memberDAO, times(1)).deleteById(42);
    }

    // Rejecting a RENEWAL request — must only update status, NOT delete member
    @Test
    void rejectRegistration_renewal_doesNotDeleteMember() {
        RegistrationRequest req = new RegistrationRequest();
        req.setRequestId(1);
        req.setMemberId(42);
        req.setType("RENEWAL");
        when(requestDAO.findPendingRegistrations())
                .thenReturn(new ArrayList<>(List.of(req)));

        memberService.rejectRegistration(1);

        verify(memberDAO, never()).deleteById(anyInt());
        verify(requestDAO, times(1)).updateRegistrationStatus(1, "REJECTED");
    }

    // Rejecting without type set — defaults to INITIAL behavior (deletes member)
    @Test
    void rejectRegistration_deletesTheMember() {
        RegistrationRequest req = new RegistrationRequest();
        req.setRequestId(1);
        req.setMemberId(42);
        when(requestDAO.findPendingRegistrations())
                .thenReturn(new ArrayList<>(List.of(req)));

        memberService.rejectRegistration(1);

        verify(requestDAO, times(1)).deleteRegistration(1);
        verify(memberDAO, times(1)).deleteById(42);
    }

    // --- Expiry Tests ---

    // Expired INITIAL registration — member and request must be deleted
    // Non-expired request — must not be touched
    @Test
    void expireOldRequests_deletesExpiredMembers() {
        RegistrationRequest expired = new RegistrationRequest();
        expired.setRequestId(1);
        expired.setMemberId(99);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));

        RegistrationRequest notExpired = new RegistrationRequest();
        notExpired.setRequestId(2);
        notExpired.setMemberId(100);
        notExpired.setExpiresAt(LocalDateTime.now().plusDays(2));

        when(requestDAO.findPendingRegistrations())
                .thenReturn(new ArrayList<>(List.of(expired, notExpired)));

        memberService.expireOldRequests();

        verify(memberDAO, times(1)).deleteById(99);   // expired → deleted
        verify(memberDAO, never()).deleteById(100);    // not expired → untouched
        verify(requestDAO, times(1)).expireOldRegistrationRequests();
    }

    // Expired tier upgrade request — must be marked EXPIRED
    // Non-expired tier upgrade — must not be touched
    @Test
    void expireOldRequests_expiresTierUpgradeRequests() {
        TierUpgradeRequest expired = new TierUpgradeRequest();
        expired.setRequestId(5);
        expired.setMemberId(10);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));

        TierUpgradeRequest notExpired = new TierUpgradeRequest();
        notExpired.setRequestId(6);
        notExpired.setMemberId(11);
        notExpired.setExpiresAt(LocalDateTime.now().plusDays(2));

        when(requestDAO.findPendingRegistrations()).thenReturn(new ArrayList<>());
        when(requestDAO.findPendingTierUpgrades())
                .thenReturn(new ArrayList<>(List.of(expired, notExpired)));

        memberService.expireOldRequests();

        verify(requestDAO, times(1)).updateTierUpgradeStatus(5, "EXPIRED");
        verify(requestDAO, never()).updateTierUpgradeStatus(6, "EXPIRED");
    }

    // --- Renewal Tests ---

    // createRenewalRequest must insert with type=RENEWAL and correct member/tier/package
    @Test
    void createRenewalRequest_insertsWithRenewalType() {
        memberService.createRenewalRequest(42, "GOLD", "MONTHLY");

        verify(requestDAO, times(1)).insertRegistration(argThat(req ->
                "RENEWAL".equals(req.getType()) &&
                        req.getMemberId() == 42 &&
                        "GOLD".equals(req.getTier()) &&
                        "MONTHLY".equals(req.getPackageType())
        ));
    }

    // --- BMI Tests ---

    // BMI formula: weight / (height in m)^2 — rounded to 2 decimal places
    @Test
    void calculateBmi_validInputs_returnsCorrectBmi() {
        double bmi = memberService.calculateBmi(70, 175);
        assertEquals(22.86, bmi, 0.01);
    }

    // BMI < 18.5 → UNDERWEIGHT
    @Test
    void getBmiCategory_underweight_returnsUnderweight() {
        assertEquals("UNDERWEIGHT", memberService.getBmiCategory(17.0));
    }

    // 18.5 ≤ BMI < 25 → NORMAL
    @Test
    void getBmiCategory_normal_returnsNormal() {
        assertEquals("NORMAL", memberService.getBmiCategory(22.0));
    }

    // 25 ≤ BMI < 30 → OVERWEIGHT
    @Test
    void getBmiCategory_overweight_returnsOverweight() {
        assertEquals("OVERWEIGHT", memberService.getBmiCategory(27.0));
    }

    // BMI ≥ 30 → OBESE
    @Test
    void getBmiCategory_obese_returnsObese() {
        assertEquals("OBESE", memberService.getBmiCategory(35.0));
    }

    // getBmiSuggestions must return exactly 3 suggestions for NORMAL category
    @Test
    void getBmiSuggestions_normal_returnsThreeSuggestions() {
        String[] suggestions = memberService.getBmiSuggestions("NORMAL");
        assertEquals(3, suggestions.length);
    }

    // Harris-Benedict calorie calculation must return a positive value
    @Test
    void calculateDailyCalories_male_returnsPositiveValue() {
        double calories = memberService.calculateDailyCalories(
                70, 175, "MALE", LocalDate.of(1995, 1, 1));
        assertTrue(calories > 0);
    }

    // --- Helper Methods ---

    // Builds a Member test object with the given parameters
    private Member buildMember(String phone, String email, LocalDate dob) {
        Member m = new Member();
        m.setMemberId(1);
        m.setFullName("Test User");
        m.setPhone(phone);
        m.setEmail(email);
        m.setDateOfBirth(dob);
        m.setPassword("$2a$12$hashedpassword");
        m.setStatus("PENDING");
        return m;
    }

    // Builds a Membership test object with the given parameters
    private Membership buildMembership(int membershipId, int memberId,
                                       String tier, int freezeCount,
                                       LocalDate endDate) {
        Membership ms = new Membership();
        ms.setMembershipId(membershipId);
        ms.setMemberId(memberId);
        ms.setTier(tier);
        ms.setFreezeCount(freezeCount);
        ms.setEndDate(endDate);
        ms.setStatus("ACTIVE");
        ms.setPackageType("MONTHLY");
        ms.setStartDate(LocalDate.now().minusDays(30));
        return ms;
    }
}