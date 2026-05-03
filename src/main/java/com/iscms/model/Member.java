package com.iscms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Member entity. Statuses: ACTIVE, PASSIVE, FROZEN, SUSPENDED, PENDING, REGISTRATION_FAILED.
// PASSIVE means the membership has expired (or was cancelled and the end date passed).
// PASSIVE members can log in to renew within 1 year, after which the account is auto-deleted.
public class Member {

    private int           memberId;
    private String        fullName;
    private LocalDate     dateOfBirth;
    private String        gender;
    private String        phone;
    private String        email;
    private String        password;
    private String        status;
    private Double        weight;
    private Double        height;
    private Double        bmiValue;
    private String        bmiCategory;
    private LocalDateTime bmiUpdatedAt;
    private String        emergencyContactName;
    private String        emergencyContactPhone;
    private LocalDateTime createdAt;
    private int           failedAttempts;
    private boolean       locked;

    // Batch 4 — cancellation lifecycle tracking
    // cancellationRequestedAt: timestamp when member clicked "Cancel Membership".
    //   Membership stays ACTIVE until end_date, then transitions to PASSIVE on next login.
    // passiveSince: timestamp of when this member entered PASSIVE state.
    //   Used to compute the 1-year window before auto-deletion.
    private LocalDateTime cancellationRequestedAt;
    private LocalDateTime passiveSince;

    // === Getters ===

    public int           getMemberId()              { return memberId; }
    public String        getFullName()              { return fullName; }
    public LocalDate     getDateOfBirth()           { return dateOfBirth; }
    public String        getGender()                { return gender; }
    public String        getPhone()                 { return phone; }
    public String        getEmail()                 { return email; }
    public String        getPassword()              { return password; }
    public String        getStatus()                { return status; }
    public Double        getWeight()                { return weight; }
    public Double        getHeight()                { return height; }
    public Double        getBmiValue()              { return bmiValue; }
    public String        getBmiCategory()           { return bmiCategory; }
    public LocalDateTime getBmiUpdatedAt()          { return bmiUpdatedAt; }
    public String        getEmergencyContactName()  { return emergencyContactName; }
    public String        getEmergencyContactPhone() { return emergencyContactPhone; }
    public LocalDateTime getCreatedAt()             { return createdAt; }
    public int           getFailedAttempts()        { return failedAttempts; }
    public boolean       isLocked()                 { return locked; }
    public LocalDateTime getCancellationRequestedAt() { return cancellationRequestedAt; }
    public LocalDateTime getPassiveSince()            { return passiveSince; }

    // === Setters ===

    public void setMemberId(int memberId)                       { this.memberId = memberId; }
    public void setFullName(String fullName)                    { this.fullName = fullName; }
    public void setDateOfBirth(LocalDate dateOfBirth)           { this.dateOfBirth = dateOfBirth; }
    public void setGender(String gender)                        { this.gender = gender; }
    public void setPhone(String phone)                          { this.phone = phone; }
    public void setEmail(String email)                          { this.email = email; }
    public void setPassword(String password)                    { this.password = password; }
    public void setStatus(String status)                        { this.status = status; }
    public void setWeight(Double weight)                        { this.weight = weight; }
    public void setHeight(Double height)                        { this.height = height; }
    public void setBmiValue(Double bmiValue)                    { this.bmiValue = bmiValue; }
    public void setBmiCategory(String bmiCategory)              { this.bmiCategory = bmiCategory; }
    public void setBmiUpdatedAt(LocalDateTime t)                { this.bmiUpdatedAt = t; }
    public void setEmergencyContactName(String name)            { this.emergencyContactName = name; }
    public void setEmergencyContactPhone(String phone)          { this.emergencyContactPhone = phone; }
    public void setCreatedAt(LocalDateTime createdAt)           { this.createdAt = createdAt; }
    public void setFailedAttempts(int failedAttempts)           { this.failedAttempts = failedAttempts; }
    public void setLocked(boolean locked)                       { this.locked = locked; }
    public void setCancellationRequestedAt(LocalDateTime t)     { this.cancellationRequestedAt = t; }
    public void setPassiveSince(LocalDateTime t)                { this.passiveSince = t; }

    // Convenience: has the member requested cancellation?
    public boolean isCancellationRequested() {
        return cancellationRequestedAt != null;
    }
}