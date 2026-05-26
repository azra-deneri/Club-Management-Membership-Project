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
    // Batch 4: InstallmentDAO added to support ANNUAL_INSTALLMENT schedule lifecycle
    @Mock private InstallmentDAO installmentDAO;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberDAO, membershipDAO, requestDAO,
                paymentDAO, installmentDAO);
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

    @Test
    void createRegistrationRequest_duplicatePhone_throwsException() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.existsByPhone("5321234567")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> memberService.createRegistrationRequest(m, "CLASSIC", "MONTHLY"));
    }

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

    @Test
    void calculateAmount_monthly_classic_returns750() {
        assertEquals(750.0, memberService.calculateAmount("CLASSIC", "MONTHLY"));
    }

    @Test
    void calculateAmount_monthly_gold_returns1250() {
        assertEquals(1250.0, memberService.calculateAmount("GOLD", "MONTHLY"));
    }

    @Test
    void calculateAmount_monthly_vip_returns2000() {
        assertEquals(2000.0, memberService.calculateAmount("VIP", "MONTHLY"));
    }

    @Test
    void calculateAmount_annualPrepaid_classic_applies15PercentDiscount() {
        double expected = 750.0 * 12 * 0.85;
        assertEquals(expected, memberService.calculateAmount("CLASSIC", "ANNUAL_PREPAID"));
    }

    @Test
    void calculateAmount_annualInstallment_classic_applies7PercentSurcharge() {
        double expected = 750.0 * 1.07;
        assertEquals(expected,
                memberService.calculateAmount("CLASSIC", "ANNUAL_INSTALLMENT"), 0.01);
    }

    @Test
    void calculateAmount_annualPrepaid_vip_applies15PercentDiscount() {
        double expected = 2000.0 * 12 * 0.85;
        assertEquals(expected, memberService.calculateAmount("VIP", "ANNUAL_PREPAID"));
    }

    // --- Freeze Tests ---

    @Test
    void freezeMembership_classicExceedsLimit_throwsException() {
        Membership ms = buildMembership(1, 1, "CLASSIC", 1,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

    @Test
    void freezeMembership_goldExceedsLimit_throwsException() {
        Membership ms = buildMembership(1, 1, "GOLD", 2,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

    @Test
    void freezeMembership_vipExceedsLimit_throwsException() {
        Membership ms = buildMembership(1, 1, "VIP", 3,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

    @Test
    void freezeMembership_within3DaysOfExpiry_throwsException() {
        Membership ms = buildMembership(1, 1, "VIP", 0,
                LocalDate.now().plusDays(2));
        when(membershipDAO.findById(1)).thenReturn(Optional.of(ms));
        assertThrows(IllegalStateException.class,
                () -> memberService.freezeMembership(1, 7));
    }

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
    // Batch 4: createTierUpgradeRequest no longer accepts a packageType parameter —
    // tier upgrades preserve the existing package, so the call is now (memberId,
    // membershipId, oldTier, newTier, fee) only.

    @Test
    void createTierUpgradeRequest_noActiveMembership_throwsException() {
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> memberService.createTierUpgradeRequest(
                        1, 1, "CLASSIC", "GOLD", 500.0));
    }

    @Test
    void createTierUpgradeRequest_validUpgrade_callsInsert() {
        Membership ms = buildMembership(1, 1, "CLASSIC", 0,
                LocalDate.now().plusDays(30));
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));

        memberService.createTierUpgradeRequest(1, 1, "CLASSIC", "GOLD", 500.0);

        verify(requestDAO).insertTierUpgrade(any());
    }

    // --- Phone Update Tests ---

    @Test
    void updatePhone_samePhone_doesNotThrow() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.findById(1)).thenReturn(Optional.of(m));
        assertDoesNotThrow(() -> memberService.updatePhone(1, "5321234567"));
    }

    @Test
    void updatePhone_differentDuplicatePhone_throwsException() {
        Member m = buildMember("5321234567", "test@test.com",
                LocalDate.now().minusYears(25));
        when(memberDAO.findById(1)).thenReturn(Optional.of(m));
        when(memberDAO.existsByPhone("5399999999")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> memberService.updatePhone(1, "5399999999"));
    }

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

    @Test
    void rejectRegistration_initial_marksRejectedAndDeletesMember() {
        RegistrationRequest req = new RegistrationRequest();
        req.setRequestId(1);
        req.setMemberId(42);
        req.setType("INITIAL");
        when(requestDAO.findPendingRegistrations())
                .thenReturn(new ArrayList<>(List.of(req)));

        memberService.rejectRegistration(1);

        // Soft delete: the request stays in the DB with REJECTED status for
        // audit, and the placeholder member is removed so the same phone/email
        // can register again.
        verify(requestDAO, times(1)).updateRegistrationStatus(1, "REJECTED");
        verify(requestDAO, never()).deleteRegistration(anyInt());
        verify(memberDAO, times(1)).deleteById(42);
    }

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

    @Test
    void rejectRegistration_defaultType_marksRejectedAndDeletesMember() {
        RegistrationRequest req = new RegistrationRequest();
        req.setRequestId(1);
        req.setMemberId(42);
        // No type set — treated as non-renewal, same path as INITIAL/NEW.
        when(requestDAO.findPendingRegistrations())
                .thenReturn(new ArrayList<>(List.of(req)));

        memberService.rejectRegistration(1);

        verify(requestDAO, times(1)).updateRegistrationStatus(1, "REJECTED");
        verify(requestDAO, never()).deleteRegistration(anyInt());
        verify(memberDAO, times(1)).deleteById(42);
    }

    // --- Expiry Tests ---

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

        verify(memberDAO, times(1)).deleteById(99);
        verify(memberDAO, never()).deleteById(100);
        verify(requestDAO, times(1)).expireOldRegistrationRequests();
    }

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

    @Test
    void calculateBmi_validInputs_returnsCorrectBmi() {
        double bmi = memberService.calculateBmi(70, 175);
        assertEquals(22.86, bmi, 0.01);
    }

    @Test
    void getBmiCategory_underweight_returnsUnderweight() {
        assertEquals("UNDERWEIGHT", memberService.getBmiCategory(17.0));
    }

    @Test
    void getBmiCategory_normal_returnsNormal() {
        assertEquals("NORMAL", memberService.getBmiCategory(22.0));
    }

    @Test
    void getBmiCategory_overweight_returnsOverweight() {
        assertEquals("OVERWEIGHT", memberService.getBmiCategory(27.0));
    }

    @Test
    void getBmiCategory_obese_returnsObese() {
        assertEquals("OBESE", memberService.getBmiCategory(35.0));
    }

    @Test
    void getBmiSuggestions_normal_returnsThreeSuggestions() {
        String[] suggestions = memberService.getBmiSuggestions("NORMAL");
        assertEquals(3, suggestions.length);
    }

    @Test
    void calculateDailyCalories_male_returnsPositiveValue() {
        double calories = memberService.calculateDailyCalories(
                70, 175, "MALE", LocalDate.of(1995, 1, 1));
        assertTrue(calories > 0);
    }

    // --- Helper Methods ---

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

    // ============================================================
    // computeMembershipEligibility — pure decision logic (no DB)
    // ============================================================

    @Test
    void computeEligibility_activeNotOnHold_canFreezeAndCancel() {
        Member m  = buildMember("5321234567", "x@x.com", LocalDate.now().minusYears(20));
        m.setStatus("ACTIVE");
        Membership ms = new Membership();
        ms.setStatus("ACTIVE");
        ms.setTier("GOLD");

        MemberService.MembershipEligibility e =
                memberService.computeMembershipEligibility(m, ms);

        assertTrue(e.canFreeze());
        assertTrue(e.canCancel());
        assertTrue(e.canUpgrade());
        assertFalse(e.canUnfreeze());
    }

    @Test
    void computeEligibility_frozenMembership_canUnfreezeOnly() {
        Member m  = buildMember("5321234567", "x@x.com", LocalDate.now().minusYears(20));
        m.setStatus("FROZEN");
        Membership ms = new Membership();
        ms.setStatus("FROZEN");
        ms.setTier("GOLD");

        MemberService.MembershipEligibility e =
                memberService.computeMembershipEligibility(m, ms);

        assertTrue(e.canUnfreeze());
        assertFalse(e.canFreeze());
        assertFalse(e.canCancel());
        assertFalse(e.canUpgrade());
    }

    @Test
    void computeEligibility_vipTier_cannotUpgrade() {
        Member m  = buildMember("5321234567", "x@x.com", LocalDate.now().minusYears(20));
        m.setStatus("ACTIVE");
        Membership ms = new Membership();
        ms.setStatus("ACTIVE");
        ms.setTier("VIP");

        MemberService.MembershipEligibility e =
                memberService.computeMembershipEligibility(m, ms);

        assertFalse(e.canUpgrade());
        assertTrue(e.canFreeze());
    }

    @Test
    void computeEligibility_pendingCancellation_cannotCancelAgain() {
        Member m  = buildMember("5321234567", "x@x.com", LocalDate.now().minusYears(20));
        m.setStatus("ACTIVE");
        m.setCancellationRequestedAt(java.time.LocalDateTime.now());
        Membership ms = new Membership();
        ms.setStatus("ACTIVE");
        ms.setTier("GOLD");

        MemberService.MembershipEligibility e =
                memberService.computeMembershipEligibility(m, ms);

        assertTrue(e.cancellationPending());
        assertFalse(e.canCancel());
    }
}