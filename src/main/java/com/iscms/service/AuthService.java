package com.iscms.service;

import com.iscms.dao.*;
import com.iscms.model.*;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;
import java.util.Optional;

// Service class responsible for all authentication operations
// Handles login for three roles: Member, Manager, Trainer
// Also handles password reset and password hashing
public class AuthService {

    private final MemberDAO memberDAO;
    private final ManagerDAO managerDAO;
    private final TrainerDAO trainerDAO;
    private final MembershipDAO membershipDAO;

    // Default constructor — creates concrete DAO implementations
    public AuthService() {
        this.memberDAO     = new MemberDAOImpl();
        this.managerDAO    = new ManagerDAOImpl();
        this.trainerDAO    = new TrainerDAOImpl();
        this.membershipDAO = new MembershipDAOImpl();
    }

    // Constructor for unit testing — allows injecting mock DAO objects
    public AuthService(MemberDAO memberDAO, ManagerDAO managerDAO,
                       TrainerDAO trainerDAO, MembershipDAO membershipDAO) {
        this.memberDAO     = memberDAO;
        this.managerDAO    = managerDAO;
        this.trainerDAO    = trainerDAO;
        this.membershipDAO = membershipDAO;
    }

    // Number of failed attempts at which password reset is suggested to member
    private static final int SUGGEST_RESET_AT   = 3;

    // Number of failed attempts at which member account is locked
    private static final int MAX_MEMBER_ATTEMPTS  = 6;

    // Number of failed attempts at which manager account is locked
    private static final int MAX_MANAGER_ATTEMPTS = 5;

    // Number of failed attempts at which trainer account is locked
    private static final int MAX_TRAINER_ATTEMPTS = 5;

    // Authenticates a member using phone number and password
    // Handles all member status checks before allowing login
    public LoginResult loginMember(String phone, String password) {

        // Check if a member with this phone number exists in the database
        Optional<Member> opt = memberDAO.findByPhone(phone);
        if (opt.isEmpty()) return LoginResult.NOT_FOUND;

        Member member = opt.get();

        // Block login for members with terminal or pending statuses
        if ("SUSPENDED".equals(member.getStatus()))           return LoginResult.SUSPENDED;
        if ("PENDING".equals(member.getStatus()))             return LoginResult.PENDING;
        if ("REGISTRATION_FAILED".equals(member.getStatus())) return LoginResult.REGISTRATION_FAILED;

        // Handle FROZEN status — check if the freeze period has ended
        if ("FROZEN".equals(member.getStatus())) {
            Optional<Membership> frozenOpt = membershipDAO.findFrozenByMemberId(member.getMemberId());
            if (frozenOpt.isPresent() && !LocalDate.now().isBefore(frozenOpt.get().getEndDate())) {
                // Freeze period has ended — automatically reactivate membership and member
                membershipDAO.updateStatus(frozenOpt.get().getMembershipId(), "ACTIVE");
                memberDAO.updateStatus(member.getMemberId(), "ACTIVE");
                // Refresh member object to reflect updated status
                member = memberDAO.findByPhone(phone).orElse(member);
            } else {
                // Freeze period is still active — deny login
                return LoginResult.FROZEN;
            }
        }

        // PASSIVE members are allowed to log in so they can submit a renewal request
        if ("PASSIVE".equals(member.getStatus())) return LoginResult.success(member);

        // Block login if account is locked due to too many failed attempts
        if (member.isLocked()) return LoginResult.LOCKED;

        // Verify the entered password against the stored BCrypt hash
        if (!BCrypt.checkpw(password, member.getPassword())) {
            int attempts = member.getFailedAttempts() + 1;
            memberDAO.updateFailedAttempts(member.getMemberId(), attempts);

            // Lock the account if maximum failed attempts reached
            if (attempts >= MAX_MEMBER_ATTEMPTS) {
                memberDAO.updateLockStatus(member.getMemberId(), true);
                return LoginResult.LOCKED;
            }

            // Suggest password reset after a certain number of failed attempts
            if (attempts == SUGGEST_RESET_AT) return LoginResult.SUGGEST_RESET;

            // Calculate and return remaining attempts before next threshold
            int remaining = attempts < SUGGEST_RESET_AT
                    ? (SUGGEST_RESET_AT - attempts)
                    : (MAX_MEMBER_ATTEMPTS - attempts);
            return LoginResult.wrong(remaining);
        }

        // Successful login — reset failed attempt counter
        memberDAO.updateFailedAttempts(member.getMemberId(), 0);

        final Member finalMember = member;

        // Check if the active membership has expired — if so, set member to PASSIVE
        membershipDAO.findActiveByMemberId(finalMember.getMemberId()).ifPresent(ms -> {
            if (ms.getEndDate().isBefore(LocalDate.now())) {
                membershipDAO.updateStatus(ms.getMembershipId(), "PASSIVE");
                memberDAO.updateStatus(finalMember.getMemberId(), "PASSIVE");
            }
        });

        // Refresh member object to capture any status changes made above
        Member refreshed = memberDAO.findByPhone(phone).orElse(member);
        return LoginResult.success(refreshed);
    }

    // Authenticates a manager using email and password
    public LoginResult loginManager(String email, String password) {
        Optional<Manager> opt = managerDAO.findByEmail(email);
        if (opt.isEmpty()) return LoginResult.NOT_FOUND;

        Manager manager = opt.get();

        // Block login if account is locked
        if (manager.isLocked()) return LoginResult.LOCKED;

        // Verify the entered password against the stored BCrypt hash
        if (!BCrypt.checkpw(password, manager.getPassword())) {
            int attempts = manager.getFailedAttempts() + 1;
            managerDAO.updateFailedAttempts(manager.getManagerId(), attempts);

            // Lock the account if maximum failed attempts reached
            if (attempts >= MAX_MANAGER_ATTEMPTS) {
                managerDAO.updateLockStatus(manager.getManagerId(), true);
                return LoginResult.LOCKED;
            }
            return LoginResult.wrong(MAX_MANAGER_ATTEMPTS - attempts);
        }

        // Successful login — reset failed attempt counter
        managerDAO.updateFailedAttempts(manager.getManagerId(), 0);
        return LoginResult.success(manager);
    }

    // Authenticates a trainer using username and password
    public LoginResult loginTrainer(String username, String password) {
        Optional<Trainer> opt = trainerDAO.findByUsername(username);
        if (opt.isEmpty()) return LoginResult.NOT_FOUND;

        Trainer trainer = opt.get();

        // Block login if account is locked
        if (trainer.isLocked()) return LoginResult.LOCKED;

        // Block login if trainer has been deactivated by manager
        if (!trainer.isActive()) return LoginResult.SUSPENDED;

        // Verify the entered password against the stored BCrypt hash
        if (!BCrypt.checkpw(password, trainer.getPassword())) {
            int attempts = trainer.getFailedAttempts() + 1;
            trainerDAO.updateFailedAttempts(trainer.getTrainerId(), attempts);

            // Lock the account if maximum failed attempts reached
            if (attempts >= MAX_TRAINER_ATTEMPTS) {
                trainerDAO.updateLockStatus(trainer.getTrainerId(), true);
                return LoginResult.LOCKED;
            }
            return LoginResult.wrong(MAX_TRAINER_ATTEMPTS - attempts);
        }

        // Successful login — reset failed attempt counter
        trainerDAO.updateFailedAttempts(trainer.getTrainerId(), 0);
        return LoginResult.success(trainer);
    }

    // Enum representing the possible outcomes of a password reset attempt
    public enum ResetResult { SUCCESS, NOT_FOUND, SAME_AS_OLD }

    // Resets a member's password identified by phone number
    // Prevents setting the same password as the current one
    public ResetResult resetMemberPassword(String phone, String newPassword) {
        Optional<Member> opt = memberDAO.findByPhone(phone);
        if (opt.isEmpty()) return ResetResult.NOT_FOUND;

        Member member = opt.get();

        // Reject if the new password is the same as the current one
        if (BCrypt.checkpw(newPassword, member.getPassword())) return ResetResult.SAME_AS_OLD;

        // Hash the new password and save it — also clears lockout state
        String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        memberDAO.resetPassword(member.getMemberId(), hashed);
        return ResetResult.SUCCESS;
    }

    // Hashes a plain text password using BCrypt with cost factor 12
    // Static so it can be called from other service classes (e.g., MemberService)
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }
}