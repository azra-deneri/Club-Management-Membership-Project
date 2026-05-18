package com.iscms.service;

import com.iscms.dao.*;
import com.iscms.model.*;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

// Service class responsible for all authentication operations.
// Handles login for three roles: Member, Manager, Trainer.
// Also handles password reset (forgot password flow) and password verification.
//
// Batch 4 changes:
//   - PASSIVE members now go through the password check (was previously skipped — security bug)
//   - PASSIVE members past their 1-year window are auto-deleted on login attempt
//   - On membership expiry (ACTIVE → PASSIVE), passive_since is stamped to start the countdown
public class AuthService {

    private final MemberDAO memberDAO;
    private final ManagerDAO managerDAO;
    private final TrainerDAO trainerDAO;
    private final MembershipDAO membershipDAO;
    private final MemberService memberService;

    public AuthService() {
        this.memberDAO     = new MemberDAOImpl();
        this.managerDAO    = new ManagerDAOImpl();
        this.trainerDAO    = new TrainerDAOImpl();
        this.membershipDAO = new MembershipDAOImpl();
        this.memberService = new MemberService();
    }

    public AuthService(MemberDAO memberDAO, ManagerDAO managerDAO,
                       TrainerDAO trainerDAO, MembershipDAO membershipDAO) {
        this(memberDAO, managerDAO, trainerDAO, membershipDAO, new MemberService());
    }

    // New constructor for tests that need to mock MemberService too
    public AuthService(MemberDAO memberDAO, ManagerDAO managerDAO,
                       TrainerDAO trainerDAO, MembershipDAO membershipDAO,
                       MemberService memberService) {
        this.memberDAO     = memberDAO;
        this.managerDAO    = managerDAO;
        this.trainerDAO    = trainerDAO;
        this.membershipDAO = membershipDAO;
        this.memberService = memberService;
    }

    private static final int SUGGEST_RESET_AT    = 3;
    private static final int MAX_MEMBER_ATTEMPTS  = 6;
    private static final int MAX_MANAGER_ATTEMPTS = 5;
    private static final int MAX_TRAINER_ATTEMPTS = 5;

    // Authenticates a member using phone number and password.
    public LoginResult loginMember(String phone, String password) {
        Optional<Member> opt = memberDAO.findByPhone(phone);
        if (opt.isEmpty()) return LoginResult.NOT_FOUND;

        Member member = opt.get();

        // Auto-delete sweep for this specific account: PASSIVE members past 1 year
        // are removed from the system the moment they try to log in. The dashboard-load
        // sweep handles the rest.
        if ("PASSIVE".equals(member.getStatus())
                && member.getPassiveSince() != null
                && member.getPassiveSince().isBefore(LocalDateTime.now().minusYears(1))) {
            memberDAO.deleteById(member.getMemberId());
            return LoginResult.NOT_FOUND;
        }

        // Terminal statuses — surface clearly without revealing password validity
        if ("SUSPENDED".equals(member.getStatus()))           return LoginResult.SUSPENDED;
        if ("PENDING".equals(member.getStatus()))             return LoginResult.PENDING;
        if ("REGISTRATION_FAILED".equals(member.getStatus())) return LoginResult.REGISTRATION_FAILED;

        // FROZEN handling — auto-thaw if the freeze period has elapsed
        if ("FROZEN".equals(member.getStatus())) {
            Optional<Membership> frozenOpt = membershipDAO.findFrozenByMemberId(member.getMemberId());
            if (frozenOpt.isPresent() && !LocalDate.now().isBefore(frozenOpt.get().getEndDate())) {
                membershipDAO.updateStatus(frozenOpt.get().getMembershipId(), "ACTIVE");
                memberDAO.updateStatus(member.getMemberId(), "ACTIVE");
                member = memberDAO.findByPhone(phone).orElse(member);
            } else {
                return LoginResult.FROZEN;
            }
        }

        // Lockout check before password verification
        if (member.isLocked()) return LoginResult.LOCKED;

        // === Password verification — now applied uniformly to ACTIVE and PASSIVE members ===
        // Previously PASSIVE members bypassed this check, allowing anyone with a phone
        // number to log in to a passive account. Fixed in Batch 4.
        if (!BCrypt.checkpw(password, member.getPassword())) {
            int attempts = member.getFailedAttempts() + 1;
            memberDAO.updateFailedAttempts(member.getMemberId(), attempts);

            if (attempts >= MAX_MEMBER_ATTEMPTS) {
                memberDAO.updateLockStatus(member.getMemberId(), true);
                return LoginResult.LOCKED;
            }
            if (attempts == SUGGEST_RESET_AT) return LoginResult.SUGGEST_RESET;

            int remaining = attempts < SUGGEST_RESET_AT
                    ? (SUGGEST_RESET_AT - attempts)
                    : (MAX_MEMBER_ATTEMPTS - attempts);
            return LoginResult.wrong(remaining);
        }

        // Password OK — reset failed attempt counter
        memberDAO.updateFailedAttempts(member.getMemberId(), 0);

        // PASSIVE members can log in for renewal — return success without further checks
        if ("PASSIVE".equals(member.getStatus())) return LoginResult.success(member);

        // ACTIVE: check if their membership has just expired and auto-passivate
        final Member finalMember = member;
        membershipDAO.findActiveByMemberId(finalMember.getMemberId()).ifPresent(ms -> {
            if (ms.getEndDate().isBefore(LocalDate.now())) {
                membershipDAO.updateStatus(ms.getMembershipId(), "PASSIVE");
                memberDAO.updateStatus(finalMember.getMemberId(), "PASSIVE");
                // Batch 4: stamp passive_since for the 1-year auto-delete countdown
                memberDAO.setPassiveSince(finalMember.getMemberId(), LocalDateTime.now());
            }
        });

// Re-fetch after the potential PASSIVE transition above. If they just went
// PASSIVE, skip the payment-hold check entirely — PASSIVE/expired members
// don't get burdened with installment-overdue restrictions on top.
        Member refreshed = memberDAO.findByPhone(phone).orElse(member);
        if (!"ACTIVE".equals(refreshed.getStatus())) {
            return LoginResult.success(refreshed);
        }

// === Payment-hold auto-check (Tur 4a) ===
// Refresh OVERDUE flags on the installment table, then test the threshold.
// If the member crosses 3+ overdue installments, we transition them to
// PAYMENT_HOLD here. The dashboard will render in restricted mode based on
// the returned status — they can still pay installments, but other tabs
// are locked until they catch up.
        memberService.markOverdueInstallments();
        memberService.checkAndApplyPaymentHold(refreshed.getMemberId());

// Re-fetch one more time in case checkAndApplyPaymentHold flipped the status
        refreshed = memberDAO.findByPhone(phone).orElse(refreshed);
        return LoginResult.success(refreshed);
    }

    // Authenticates a manager using email and password.
    // ADMIN accounts are exempt from lockout — see Batch 1 fix.
    public LoginResult loginManager(String email, String password) {
        Optional<Manager> opt = managerDAO.findByEmail(email);
        if (opt.isEmpty()) return LoginResult.NOT_FOUND;

        Manager manager = opt.get();
        boolean isAdmin = "ADMIN".equals(manager.getRole());

        if (!isAdmin && manager.isLocked()) return LoginResult.LOCKED;

        if (!BCrypt.checkpw(password, manager.getPassword())) {
            if (isAdmin) {
                return LoginResult.wrong(-1);
            }
            int attempts = manager.getFailedAttempts() + 1;
            managerDAO.updateFailedAttempts(manager.getManagerId(), attempts);

            if (attempts >= MAX_MANAGER_ATTEMPTS) {
                managerDAO.updateLockStatus(manager.getManagerId(), true);
                return LoginResult.LOCKED;
            }
            return LoginResult.wrong(MAX_MANAGER_ATTEMPTS - attempts);
        }

        managerDAO.updateFailedAttempts(manager.getManagerId(), 0);
        return LoginResult.success(manager);
    }

    public LoginResult loginTrainer(String username, String password) {
        Optional<Trainer> opt = trainerDAO.findByUsername(username);
        if (opt.isEmpty()) return LoginResult.NOT_FOUND;

        Trainer trainer = opt.get();
        if (trainer.isLocked()) return LoginResult.LOCKED;
        if (!trainer.isActive()) return LoginResult.SUSPENDED;

        if (!BCrypt.checkpw(password, trainer.getPassword())) {
            int attempts = trainer.getFailedAttempts() + 1;
            trainerDAO.updateFailedAttempts(trainer.getTrainerId(), attempts);

            if (attempts >= MAX_TRAINER_ATTEMPTS) {
                trainerDAO.updateLockStatus(trainer.getTrainerId(), true);
                return LoginResult.LOCKED;
            }
            return LoginResult.wrong(MAX_TRAINER_ATTEMPTS - attempts);
        }
        trainerDAO.updateFailedAttempts(trainer.getTrainerId(), 0);
        return LoginResult.success(trainer);
    }

    public enum ResetResult { SUCCESS, NOT_FOUND, SAME_AS_OLD }

    public ResetResult resetMemberPassword(String phone, String newPassword) {
        Optional<Member> opt = memberDAO.findByPhone(phone);
        if (opt.isEmpty()) return ResetResult.NOT_FOUND;
        Member member = opt.get();
        if (BCrypt.checkpw(newPassword, member.getPassword())) return ResetResult.SAME_AS_OLD;
        String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        memberDAO.resetPassword(member.getMemberId(), hashed);
        return ResetResult.SUCCESS;
    }

    public ResetResult resetManagerPasswordByEmail(String email, String newPassword) {
        Optional<Manager> opt = managerDAO.findByEmail(email);
        if (opt.isEmpty()) return ResetResult.NOT_FOUND;
        Manager manager = opt.get();
        if (BCrypt.checkpw(newPassword, manager.getPassword())) return ResetResult.SAME_AS_OLD;
        String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        managerDAO.updatePassword(manager.getManagerId(), hashed);
        managerDAO.updateLockStatus(manager.getManagerId(), false);
        managerDAO.updateFailedAttempts(manager.getManagerId(), 0);
        return ResetResult.SUCCESS;
    }

    public ResetResult resetTrainerPasswordByUsername(String username, String newPassword) {
        Optional<Trainer> opt = trainerDAO.findByUsername(username);
        if (opt.isEmpty()) return ResetResult.NOT_FOUND;
        Trainer trainer = opt.get();
        if (BCrypt.checkpw(newPassword, trainer.getPassword())) return ResetResult.SAME_AS_OLD;
        String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        trainerDAO.updatePassword(trainer.getTrainerId(), hashed);
        trainerDAO.updateLockStatus(trainer.getTrainerId(), false);
        trainerDAO.updateFailedAttempts(trainer.getTrainerId(), 0);
        return ResetResult.SUCCESS;
    }

    public boolean verifyManagerPassword(int managerId, String plainPassword) {
        return managerDAO.findAll().stream()
                .filter(m -> m.getManagerId() == managerId)
                .findFirst()
                .map(m -> BCrypt.checkpw(plainPassword, m.getPassword()))
                .orElse(false);
    }

    public boolean verifyTrainerPassword(int trainerId, String plainPassword) {
        return trainerDAO.findById(trainerId)
                .map(t -> BCrypt.checkpw(plainPassword, t.getPassword()))
                .orElse(false);
    }

    public boolean verifyMemberPassword(int memberId, String plainPassword) {
        return memberDAO.findById(memberId)
                .map(m -> BCrypt.checkpw(plainPassword, m.getPassword()))
                .orElse(false);
    }

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }
}