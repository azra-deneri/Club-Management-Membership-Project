package com.iscms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Model class representing a sports club member
// Maps to the member table in the database
public class Member {

    // Unique identifier for the member (maps to member_id in DB)
    private int memberId;

    // Full display name of the member
    private String fullName;

    // Date of birth — used for age validation (BR-01: must be 18+)
    private LocalDate dateOfBirth;

    // Gender of the member (e.g., MALE, FEMALE)
    private String gender;

    // Unique phone number (UNIQUE constraint in DB)
    private String phone;

    // Unique email address (UNIQUE constraint in DB)
    private String email;

    // BCrypt-hashed password (never stored as plain text)
    private String password;

    // Body weight in kg — nullable (Double, not double) because member may not have set it yet
    private Double weight;

    // Height in cm — nullable for the same reason as weight
    private Double height;

    // Calculated BMI value — nullable until member provides weight and height
    private Double bmiValue;

    // BMI category label (e.g., Underweight, Normal, Overweight, Obese)
    private String bmiCategory;

    // Timestamp of the last BMI calculation
    private LocalDateTime bmiUpdatedAt;

    // Name of the member's emergency contact person
    private String emergencyContactName;

    // Phone number of the member's emergency contact
    private String emergencyContactPhone;

    // Current account status:
    // ACTIVE, PASSIVE, SUSPENDED, PENDING, REGISTRATION_FAILED, FROZEN
    private String status;

    // Number of consecutive failed login attempts (used for lockout logic)
    private int failedAttempts;

    // Whether this account is locked (true = cannot log in)
    private boolean isLocked;

    // Timestamp of when this member account was created
    private LocalDateTime createdAt;

    // No-arg constructor required for JDBC result mapping
    public Member() {}

    // --- Getters and Setters ---

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // Nullable Double — member may not have entered weight yet
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    // Nullable Double — member may not have entered height yet
    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    // Nullable Double — calculated only after weight and height are provided
    public Double getBmiValue() { return bmiValue; }
    public void setBmiValue(Double bmiValue) { this.bmiValue = bmiValue; }

    public String getBmiCategory() { return bmiCategory; }
    public void setBmiCategory(String bmiCategory) { this.bmiCategory = bmiCategory; }

    public LocalDateTime getBmiUpdatedAt() { return bmiUpdatedAt; }
    public void setBmiUpdatedAt(LocalDateTime bmiUpdatedAt) { this.bmiUpdatedAt = bmiUpdatedAt; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String v) { this.emergencyContactName = v; }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String v) { this.emergencyContactPhone = v; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    // Boolean getter uses 'is' prefix — standard Java naming convention
    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}