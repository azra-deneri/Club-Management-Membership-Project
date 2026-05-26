package com.iscms;

import com.iscms.dao.MemberDAO;
import com.iscms.dao.ManagerDAO;
import com.iscms.dao.MembershipDAO;
import com.iscms.dao.TrainerDAO;
import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.model.Trainer;
import com.iscms.service.AuthService;
import com.iscms.service.LoginResult;
import com.iscms.service.MemberService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Unit tests for AuthService — covers all login flows for Member, Manager, and Trainer
// Uses Mockito to mock DAO interfaces — no real DB calls
@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    // Mocked DAO interfaces — injected into AuthService via constructor
    @Mock private MemberDAO memberDAO;
    @Mock private ManagerDAO managerDAO;
    @Mock private TrainerDAO trainerDAO;
    @Mock private MembershipDAO membershipDAO;
    // MemberService is also mocked — loginMember calls markOverdueInstallments
    // and checkAndApplyPaymentHold on the service, and those would otherwise
    // hit a real DAO + DBConnection during the test.
    @Mock private MemberService memberService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Inject mocks via the 5-arg constructor — avoids real DAO instantiation
        // and isolates AuthService from MemberService's side effects (overdue
        // installment marking, payment-hold check) during these tests.
        authService = new AuthService(memberDAO, managerDAO, trainerDAO,
                membershipDAO, memberService);
    }
    // --- Member Login Tests ---

    // No account found with the given phone number
    @Test
    void loginMember_unknownPhone_returnsNotFound() {
        when(memberDAO.findByPhone("0000000000")).thenReturn(Optional.empty());
        assertEquals(LoginResult.Status.NOT_FOUND,
                authService.loginMember("0000000000", "any").getStatus());
    }

    // Member account is suspended — login blocked
    @Test
    void loginMember_suspendedMember_returnsSuspended() {
        Member m = buildMember("SUSPENDED", false, 0, "hash");
        when(memberDAO.findByPhone("5551111111")).thenReturn(Optional.of(m));
        assertEquals(LoginResult.Status.SUSPENDED,
                authService.loginMember("5551111111", "any").getStatus());
    }

    // Member registration is still pending manager approval
    @Test
    void loginMember_pendingMember_returnsPending() {
        Member m = buildMember("PENDING", false, 0, "hash");
        when(memberDAO.findByPhone("5552222222")).thenReturn(Optional.of(m));
        assertEquals(LoginResult.Status.PENDING,
                authService.loginMember("5552222222", "any").getStatus());
    }

    // Member account is locked due to too many failed attempts
    @Test
    void loginMember_lockedMember_returnsLocked() {
        Member m = buildMember("ACTIVE", true, 6, "hash");
        when(memberDAO.findByPhone("5554444444")).thenReturn(Optional.of(m));
        assertEquals(LoginResult.Status.LOCKED,
                authService.loginMember("5554444444", "any").getStatus());
    }

    // Wrong password — failed attempt counter must be incremented
    @Test
    void loginMember_wrongPassword_incrementsAttempts() {
        String hashed = AuthService.hashPassword("correctPass");
        Member m = buildMember("ACTIVE", false, 0, hashed);
        when(memberDAO.findByPhone("5555555555")).thenReturn(Optional.of(m));

        LoginResult result = authService.loginMember("5555555555", "wrongPass");

        assertEquals(LoginResult.Status.WRONG_PASSWORD, result.getStatus());
        verify(memberDAO).updateFailedAttempts(m.getMemberId(), 1);
    }

    // Third wrong attempt — system suggests password reset
    @Test
    void loginMember_thirdWrongAttempt_returnsSuggestReset() {
        String hashed = AuthService.hashPassword("correctPass");
        Member m = buildMember("ACTIVE", false, 2, hashed); // 2 existing → 3rd attempt
        when(memberDAO.findByPhone("5556666666")).thenReturn(Optional.of(m));

        assertEquals(LoginResult.Status.SUGGEST_RESET,
                authService.loginMember("5556666666", "wrongPass").getStatus());
    }

    // Sixth wrong attempt — account must be locked
    @Test
    void loginMember_sixthWrongAttempt_locksAccount() {
        String hashed = AuthService.hashPassword("correctPass");
        Member m = buildMember("ACTIVE", false, 5, hashed); // 5 existing → 6th attempt
        when(memberDAO.findByPhone("5557777777")).thenReturn(Optional.of(m));

        LoginResult result = authService.loginMember("5557777777", "wrongPass");

        assertEquals(LoginResult.Status.LOCKED, result.getStatus());
        verify(memberDAO).updateLockStatus(m.getMemberId(), true);
    }

    // Correct password — login succeeds and failed attempts reset to 0
    @Test
    void loginMember_correctPassword_returnsSuccess() {
        String hashed = AuthService.hashPassword("myPassword");
        Member m = buildMember("ACTIVE", false, 0, hashed);
        when(memberDAO.findByPhone("5558888888")).thenReturn(Optional.of(m));
        when(membershipDAO.findActiveByMemberId(m.getMemberId())).thenReturn(Optional.empty());

        LoginResult result = authService.loginMember("5558888888", "myPassword");

        assertEquals(LoginResult.Status.SUCCESS, result.getStatus());
        verify(memberDAO).updateFailedAttempts(m.getMemberId(), 0);
    }

    // Expired membership — member and membership set to PASSIVE but login still succeeds
    @Test
    void loginMember_expiredMembership_memberBecomesPassiveButLoginSucceeds() {
        String hashed = AuthService.hashPassword("myPassword");
        Member m = buildMember("ACTIVE", false, 0, hashed);
        when(memberDAO.findByPhone("5559999999")).thenReturn(Optional.of(m));

        Membership ms = new Membership();
        ms.setMembershipId(1);
        ms.setMemberId(99);
        ms.setPackageType("MONTHLY");
        ms.setEndDate(LocalDate.now().minusDays(1)); // expired yesterday

        when(membershipDAO.findActiveByMemberId(m.getMemberId())).thenReturn(Optional.of(ms));
        when(memberDAO.findByPhone("5559999999")).thenReturn(Optional.of(m));

        LoginResult result = authService.loginMember("5559999999", "myPassword");

        // Both membership and member must be set to PASSIVE
        verify(membershipDAO).updateStatus(1, "PASSIVE");
        verify(memberDAO).updateStatus(99, "PASSIVE");
        // Login still succeeds — member can log in to submit renewal
        assertEquals(LoginResult.Status.SUCCESS, result.getStatus());
    }

    // Active membership — no PASSIVE transition should occur
    @Test
    void loginMember_activeMembership_noPassiveTransition() {
        String hashed = AuthService.hashPassword("myPassword");
        Member m = buildMember("ACTIVE", false, 0, hashed);
        when(memberDAO.findByPhone("5559999998")).thenReturn(Optional.of(m));

        Membership ms = new Membership();
        ms.setMembershipId(2);
        ms.setMemberId(99);
        ms.setPackageType("MONTHLY");
        ms.setEndDate(LocalDate.now().plusDays(10)); // still active

        when(membershipDAO.findActiveByMemberId(m.getMemberId())).thenReturn(Optional.of(ms));
        when(memberDAO.findByPhone("5559999998")).thenReturn(Optional.of(m));

        LoginResult result = authService.loginMember("5559999998", "myPassword");

        // PASSIVE update must NOT be called
        verify(membershipDAO, never()).updateStatus(anyInt(), eq("PASSIVE"));
        assertEquals(LoginResult.Status.SUCCESS, result.getStatus());
    }

    // --- Manager Login Tests ---

    // No account found with the given email
    @Test
    void loginManager_unknownEmail_returnsNotFound() {
        when(managerDAO.findByEmail("nobody@test.com")).thenReturn(Optional.empty());
        assertEquals(LoginResult.Status.NOT_FOUND,
                authService.loginManager("nobody@test.com", "any").getStatus());
    }

    // Manager account is locked
    @Test
    void loginManager_lockedAccount_returnsLocked() {
        Manager m = buildManager(true, 5);
        when(managerDAO.findByEmail("m@test.com")).thenReturn(Optional.of(m));
        assertEquals(LoginResult.Status.LOCKED,
                authService.loginManager("m@test.com", "any").getStatus());
    }

    // Wrong password — failed attempt counter must be incremented
    @Test
    void loginManager_wrongPassword_incrementsAttempts() {
        String hashed = AuthService.hashPassword("correct");
        Manager m = buildManager(false, 0);
        m.setPassword(hashed);
        when(managerDAO.findByEmail("m@test.com")).thenReturn(Optional.of(m));

        LoginResult result = authService.loginManager("m@test.com", "wrong");

        assertEquals(LoginResult.Status.WRONG_PASSWORD, result.getStatus());
        verify(managerDAO).updateFailedAttempts(m.getManagerId(), 1);
    }

    // Fifth wrong attempt — manager account must be locked
    @Test
    void loginManager_fifthWrongAttempt_locksAccount() {
        String hashed = AuthService.hashPassword("correct");
        Manager m = buildManager(false, 4); // 4 existing → 5th attempt
        m.setPassword(hashed);
        when(managerDAO.findByEmail("m@test.com")).thenReturn(Optional.of(m));

        LoginResult result = authService.loginManager("m@test.com", "wrong");

        assertEquals(LoginResult.Status.LOCKED, result.getStatus());
        verify(managerDAO).updateLockStatus(m.getManagerId(), true);
    }

    // Correct password — login succeeds and failed attempts reset to 0
    @Test
    void loginManager_correctPassword_returnsSuccess() {
        String hashed = AuthService.hashPassword("correct");
        Manager m = buildManager(false, 0);
        m.setPassword(hashed);
        when(managerDAO.findByEmail("m@test.com")).thenReturn(Optional.of(m));

        LoginResult result = authService.loginManager("m@test.com", "correct");

        assertEquals(LoginResult.Status.SUCCESS, result.getStatus());
        verify(managerDAO).updateFailedAttempts(m.getManagerId(), 0);
    }

    // --- Trainer Login Tests ---

    // No account found with the given username
    @Test
    void loginTrainer_unknownUsername_returnsNotFound() {
        when(trainerDAO.findByUsername("nobody")).thenReturn(Optional.empty());
        assertEquals(LoginResult.Status.NOT_FOUND,
                authService.loginTrainer("nobody", "any").getStatus());
    }

    // Trainer is deactivated by manager — login blocked
    @Test
    void loginTrainer_inactiveTrainer_returnsSuspended() {
        Trainer t = buildTrainer(false, false, 0); // isActive = false
        when(trainerDAO.findByUsername("trainer1")).thenReturn(Optional.of(t));
        assertEquals(LoginResult.Status.SUSPENDED,
                authService.loginTrainer("trainer1", "any").getStatus());
    }

    // Correct password — login succeeds and failed attempts reset to 0
    @Test
    void loginTrainer_correctPassword_returnsSuccess() {
        String hashed = AuthService.hashPassword("correct");
        Trainer t = buildTrainer(false, true, 0);
        t.setPassword(hashed);
        when(trainerDAO.findByUsername("trainer1")).thenReturn(Optional.of(t));

        LoginResult result = authService.loginTrainer("trainer1", "correct");

        assertEquals(LoginResult.Status.SUCCESS, result.getStatus());
        verify(trainerDAO).updateFailedAttempts(t.getTrainerId(), 0);
    }

    // --- BCrypt / Password Tests ---

    // hashPassword must produce a valid BCrypt hash starting with $2a$
    @Test
    void hashPassword_producesValidBcryptPrefix() {
        String hash = AuthService.hashPassword("test123");
        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"));
    }

    // hashPassword must use a different salt each time — same input, different hash
    @Test
    void hashPassword_differentSaltEachTime() {
        assertNotEquals(
                AuthService.hashPassword("same"),
                AuthService.hashPassword("same"));
    }

    // --- Helper Methods ---

    // Builds a Member test object with the given parameters
    private Member buildMember(String status, boolean locked,
                               int failedAttempts, String password) {
        Member m = new Member();
        m.setMemberId(99);
        m.setStatus(status);
        m.setLocked(locked);
        m.setFailedAttempts(failedAttempts);
        m.setPassword(password);
        m.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return m;
    }

    // Builds a Manager test object with the given parameters
    private Manager buildManager(boolean locked, int failedAttempts) {
        Manager m = new Manager();
        m.setManagerId(10);
        m.setLocked(locked);
        m.setFailedAttempts(failedAttempts);
        m.setPassword("placeholder");
        return m;
    }

    // Builds a Trainer test object with the given parameters
    private Trainer buildTrainer(boolean locked, boolean active, int failedAttempts) {
        Trainer t = new Trainer();
        t.setTrainerId(20);
        t.setLocked(locked);
        t.setActive(active);
        t.setFailedAttempts(failedAttempts);
        t.setPassword("placeholder");
        return t;
    }

    // --- changeTrainerPasswordSelf Tests (G1) ---
    // These tests verify that all business rules for trainer password change
    // live in AuthService (not in the controller): length check, current
    // password verification, same-as-old rejection, and DAO persistence.

    @Test
    void changeTrainerPasswordSelf_validRequest_returnsSuccessAndHashesPassword() {
        // Arrange: trainer with BCrypt hash of "oldpass1"
        String oldHash = org.mindrot.jbcrypt.BCrypt.hashpw("oldpass1",
                org.mindrot.jbcrypt.BCrypt.gensalt(4));
        Trainer t = new Trainer();
        t.setTrainerId(1);
        t.setPassword(oldHash);

        // Act
        AuthService.ResetResult result =
                authService.changeTrainerPasswordSelf(t, "oldpass1", "newpass2");

        // Assert
        assertEquals(AuthService.ResetResult.SUCCESS, result);
        verify(trainerDAO).updatePassword(eq(1), anyString());
        // Session-sync contract: in-memory trainer object reflects new hash
        assertTrue(org.mindrot.jbcrypt.BCrypt.checkpw("newpass2", t.getPassword()));
    }

    @Test
    void changeTrainerPasswordSelf_tooShortPassword_throwsAndDoesNotPersist() {
        String oldHash = org.mindrot.jbcrypt.BCrypt.hashpw("oldpass1",
                org.mindrot.jbcrypt.BCrypt.gensalt(4));
        Trainer t = new Trainer();
        t.setTrainerId(1);
        t.setPassword(oldHash);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changeTrainerPasswordSelf(t, "oldpass1", "abc"));
        assertTrue(ex.getMessage().toLowerCase().contains("6 characters"));
        verify(trainerDAO, never()).updatePassword(anyInt(), anyString());
    }

    @Test
    void changeTrainerPasswordSelf_wrongCurrentPassword_throwsAndDoesNotPersist() {
        String oldHash = org.mindrot.jbcrypt.BCrypt.hashpw("oldpass1",
                org.mindrot.jbcrypt.BCrypt.gensalt(4));
        Trainer t = new Trainer();
        t.setTrainerId(1);
        t.setPassword(oldHash);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.changeTrainerPasswordSelf(t, "wrongpass", "newpass2"));
        assertTrue(ex.getMessage().toLowerCase().contains("incorrect"));
        verify(trainerDAO, never()).updatePassword(anyInt(), anyString());
    }

    @Test
    void changeTrainerPasswordSelf_sameAsOld_returnsSameAsOldAndDoesNotPersist() {
        String oldHash = org.mindrot.jbcrypt.BCrypt.hashpw("samepass",
                org.mindrot.jbcrypt.BCrypt.gensalt(4));
        Trainer t = new Trainer();
        t.setTrainerId(1);
        t.setPassword(oldHash);

        AuthService.ResetResult result =
                authService.changeTrainerPasswordSelf(t, "samepass", "samepass");

        assertEquals(AuthService.ResetResult.SAME_AS_OLD, result);
        verify(trainerDAO, never()).updatePassword(anyInt(), anyString());
    }
}