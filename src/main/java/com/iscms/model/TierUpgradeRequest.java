package com.iscms.model;

import java.time.LocalDateTime;

// Model class representing a tier upgrade request submitted by a member (POJO)
// Maps to the tier_upgrade_request table in the database
// Example: member wants to upgrade from CLASSIC → GOLD
public class TierUpgradeRequest {

    // Unique identifier for this upgrade request (maps to request_id in DB)
    private int requestId;

    // ID of the member who submitted the upgrade request (FK → member table)
    private int memberId;

    // ID of the membership being upgraded (FK → membership table)
    private int membershipId;

    // The member's current tier before the upgrade (CLASSIC, GOLD, or VIP)
    private String oldTier;

    // The tier the member wants to upgrade to (CLASSIC, GOLD, or VIP)
    private String newTier;

    // The payment package type of the current membership (MONTHLY, ANNUAL_INSTALLMENT, ANNUAL_PREPAID)
    // Carried over from the existing membership to apply correct pricing
    private String packageType;

    // Fee charged for the tier upgrade — recorded as a payment upon approval
    private double upgradeFee;

    // Current status of the request: PENDING, APPROVED, REJECTED, or EXPIRED
    // Requests automatically expire after 3 days if not acted on (BR-35)
    private String status;

    // Timestamp after which this request is considered expired (BR-35: 3-day window)
    // DB type is timestamp — mapped to LocalDateTime in Java
    private LocalDateTime expiresAt;

    // Timestamp of when this upgrade request was created
    private LocalDateTime createdAt;

    // No-arg constructor required for JDBC result mapping
    public TierUpgradeRequest() {}

    // --- Getters and Setters ---

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getMembershipId() { return membershipId; }
    public void setMembershipId(int membershipId) { this.membershipId = membershipId; }

    public String getOldTier() { return oldTier; }
    public void setOldTier(String oldTier) { this.oldTier = oldTier; }

    public String getNewTier() { return newTier; }
    public void setNewTier(String newTier) { this.newTier = newTier; }

    // Package type is needed to calculate the correct upgrade fee at approval time
    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }

    public double getUpgradeFee() { return upgradeFee; }
    public void setUpgradeFee(double upgradeFee) { this.upgradeFee = upgradeFee; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}