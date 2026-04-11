package com.iscms.model;

import java.time.LocalDateTime;

// Model class representing a membership registration or renewal request (POJO)
// Maps to the registration_request table in the database
// Used for two flows: initial signup (INITIAL) and membership renewal (RENEWAL)
public class RegistrationRequest {

    // Unique identifier for this request (maps to request_id in DB)
    private int requestId;

    // ID of the member who submitted this request (FK → member table)
    private int memberId;

    // Requested membership tier: CLASSIC, GOLD, or VIP
    private String tier;

    // Requested payment package: MONTHLY, ANNUAL_INSTALLMENT, or ANNUAL_PREPAID
    private String packageType;

    // Amount to be charged upon manager approval
    private double amount;

    // Current status of the request: PENDING, APPROVED, REJECTED, or EXPIRED
    // Requests automatically expire after 3 days if not acted on (BR-35)
    private String status;

    // Timestamp after which this request is considered expired (BR-35: 3-day window)
    // DB type is timestamp — mapped to LocalDateTime in Java
    private LocalDateTime expiresAt;

    // Timestamp of when this request was created
    private LocalDateTime createdAt;

    // Type of registration request — stored in DB as enum
    // INITIAL: member's first-time membership registration
    // RENEWAL: member requesting to renew an expired or expiring membership
    private String type;

    // No-arg constructor required for JDBC result mapping
    public RegistrationRequest() {}

    // --- Getters and Setters ---

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Distinguishes between first-time registration and renewal in both DB and UI
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}