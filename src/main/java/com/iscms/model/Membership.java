package com.iscms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Model class representing a member's membership record
// One member can have one active membership at a time
public class Membership {

    // Unique identifier for this membership record (maps to membership_id in DB)
    private int membershipId;

    // ID of the member who owns this membership (FK → member table)
    private int memberId;

    // Membership tier level: CLASSIC, GOLD, or VIP
    private String tier;

    // Payment package type: MONTHLY, ANNUAL_INSTALLMENT, or ANNUAL_PREPAID
    private String packageType;

    // Date the membership period starts
    private LocalDate startDate;

    // Date the membership period ends
    private LocalDate endDate;

    // Current status of the membership: ACTIVE, PASSIVE, SUSPENDED, or FROZEN
    private String status;

    // Number of times this membership has been frozen
    // Used to enforce freeze limits per business rules (BR-06)
    private int freezeCount;

    // Timestamp of when this membership record was created
    private LocalDateTime createdAt;

    // No-arg constructor required for JDBC result mapping
    public Membership() {}

    // --- Getters and Setters ---

    public int getMembershipId() { return membershipId; }
    public void setMembershipId(int membershipId) { this.membershipId = membershipId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    // Tier determines which events and PT slots the member can access
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    // Package type determines billing cycle and duration
    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Freeze count is incremented each time the member freezes their membership
    public int getFreezeCount() { return freezeCount; }
    public void setFreezeCount(int freezeCount) { this.freezeCount = freezeCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}