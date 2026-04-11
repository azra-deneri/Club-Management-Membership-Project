package com.iscms.model;

import java.time.LocalDateTime;

// Model class representing a payment record in the system (POJO)
// Maps to the payment table in the database
public class Payment {

    // Unique identifier for this payment record (maps to payment_id in DB)
    private int paymentId;

    // ID of the member who made this payment (FK → member table)
    private int memberId;

    // Payment amount — primitive double because amount can never be null
    private double amount;

    // Timestamp of when the payment was made
    private LocalDateTime paymentDate;

    // Type of payment: MEMBERSHIP (new/renewal) or UPGRADE (tier upgrade)
    private String paymentType;

    // Optional human-readable description of the payment
    private String description;

    // Current payment status: PAID or PENDING
    private String status;

    // ID of the manager who recorded this payment (FK → manager table)
    // Stored as recordedBy — maps to recorded_by column in DB
    private int recordedBy;

    // No-arg constructor required for JDBC result mapping
    public Payment() {}

    // --- Getters and Setters ---

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }

    // Payment type determines the context: membership registration or tier upgrade
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // The manager who approved and recorded this payment
    public int getRecordedBy() { return recordedBy; }
    public void setRecordedBy(int recordedBy) { this.recordedBy = recordedBy; }
}