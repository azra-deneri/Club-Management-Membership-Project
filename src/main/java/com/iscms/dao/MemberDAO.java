package com.iscms.dao;

import com.iscms.model.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// DAO interface for member database operations.
// Batch 4 adds methods for cancellation tracking and the 1-year passive expiry flow.
public interface MemberDAO {

    void insert(Member member);

    Optional<Member> findByPhone(String phone);

    Optional<Member> findByEmail(String email);

    Optional<Member> findById(int memberId);

    List<Member> findAll();

    List<Member> findByStatus(String status);

    void updateStatus(int memberId, String status);

    void updateFailedAttempts(int memberId, int attempts);

    void updateLockStatus(int memberId, boolean locked);

    void updateBmi(int memberId, double bmiValue, String bmiCategory);

    void updateProfile(int memberId, Double weight, Double height,
                       String ecName, String ecPhone);

    void resetPassword(int memberId, String hashedPassword);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    void updatePhone(int memberId, String phone);

    void updateEmail(int memberId, String email);

    void deleteById(int memberId);

    // === Batch 4 additions ===

    // Records that the member requested cancellation. Membership stays active until
    // end_date — only on next login does the system actually transition to PASSIVE.
    void setCancellationRequested(int memberId, LocalDateTime when);

    // Clears the cancellation flag — called when a passive member renews their
    // membership (they changed their mind or returned later).
    void clearCancellationRequested(int memberId);

    // Stamps the moment the member entered PASSIVE state. Used by the 1-year
    // auto-delete check on subsequent logins.
    void setPassiveSince(int memberId, LocalDateTime when);

    // Returns members whose passive_since is older than the cutoff timestamp.
    // Caller iterates and deletes them via deleteById().
    List<Member> findPassiveOlderThan(LocalDateTime cutoff);
}