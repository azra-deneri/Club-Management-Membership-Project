package com.iscms.dao;

import com.iscms.model.Member;
import java.util.List;
import java.util.Optional;

// DAO interface for member database operations
// Follows the DAO pattern: all SQL stays in the implementation, not in the service layer
public interface MemberDAO {

    // Insert a new member record into the database
    void insert(Member member);

    // Find a member by phone number — used during login and duplicate phone check
    Optional<Member> findByPhone(String phone);

    // Find a member by email address — used during login and duplicate email check
    Optional<Member> findByEmail(String email);

    // Find a member by their ID — returns Optional.empty() if not found
    Optional<Member> findById(int memberId);

    // Return all member records — used in manager dashboard member list
    List<Member> findAll();

    // Return all members with a specific status (e.g., ACTIVE, FROZEN, SUSPENDED, PENDING)
    // Used for filtered views in manager and report panels
    List<Member> findByStatus(String status);

    // Update the account status of a member
    // Valid values: ACTIVE, PASSIVE, SUSPENDED, PENDING, REGISTRATION_FAILED, FROZEN
    void updateStatus(int memberId, String status);

    // Update the number of consecutive failed login attempts for a member
    // Used for lockout logic (BR-03)
    void updateFailedAttempts(int memberId, int attempts);

    // Lock or unlock a member account
    // true = locked (cannot log in), false = unlocked
    void updateLockStatus(int memberId, boolean locked);

    // Update the calculated BMI value and category for a member
    // Called after member updates weight or height (UC-M13)
    void updateBmi(int memberId, double bmiValue, String bmiCategory);

    // Update editable profile fields for a member
    // Covers: weight, height, emergency contact name, emergency contact phone
    void updateProfile(int memberId, Double weight, Double height,
                       String ecName, String ecPhone);

    // Update the hashed password for a member
    // Always stores BCrypt hash — plain text passwords are never stored
    void resetPassword(int memberId, String hashedPassword);

    // Check whether a phone number is already registered in the system
    // Used for duplicate phone validation
    boolean existsByPhone(String phone);

    // Check whether an email address is already registered in the system
    // Used for duplicate email validation
    boolean existsByEmail(String email);

    // Update a member's phone number — separate from updateProfile
    void updatePhone(int memberId, String phone);

    // Update a member's email address — separate from updateProfile
    void updateEmail(int memberId, String email);

    // Delete a member record by ID
    void deleteById(int memberId);
}