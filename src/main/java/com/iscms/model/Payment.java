package com.iscms.model;

import java.time.LocalDateTime;

// Represents a single payment record. Used for membership payments, tier upgrades,
// and individual installment payments. The payment_method column distinguishes
// CASH (manager-recorded at the club) from ONLINE (mock card payment by the member).
// installment_id, when non-null, links a payment to the specific installment it cleared.
public class Payment {

    private int           paymentId;
    private int           memberId;        // 0 when member has been deleted (FK SET NULL → reads as 0)
    private double        amount;
    private LocalDateTime paymentDate;
    private String        paymentType;     // MEMBERSHIP | UPGRADE | INSTALLMENT
    private String        description;
    private String        status;          // PAID | PENDING | FAILED
    private int           recordedBy;      // 0 means online self-payment (no manager involved)

    // Batch 4 fields
    private String        paymentMethod;   // CASH | ONLINE
    private Integer       installmentId;   // FK to installment if this payment cleared an installment

    // === Getters ===

    public int           getPaymentId()     { return paymentId; }
    public int           getMemberId()      { return memberId; }
    public double        getAmount()        { return amount; }
    public LocalDateTime getPaymentDate()   { return paymentDate; }
    public String        getPaymentType()   { return paymentType; }
    public String        getDescription()   { return description; }
    public String        getStatus()        { return status; }
    public int           getRecordedBy()    { return recordedBy; }
    public String        getPaymentMethod() { return paymentMethod; }
    public Integer       getInstallmentId() { return installmentId; }

    // === Setters ===

    public void setPaymentId(int paymentId)             { this.paymentId = paymentId; }
    public void setMemberId(int memberId)               { this.memberId = memberId; }
    public void setAmount(double amount)                { this.amount = amount; }
    public void setPaymentDate(LocalDateTime date)      { this.paymentDate = date; }
    public void setPaymentType(String paymentType)      { this.paymentType = paymentType; }
    public void setDescription(String description)      { this.description = description; }
    public void setStatus(String status)                { this.status = status; }
    public void setRecordedBy(int recordedBy)           { this.recordedBy = recordedBy; }
    public void setPaymentMethod(String paymentMethod)  { this.paymentMethod = paymentMethod; }
    public void setInstallmentId(Integer installmentId) { this.installmentId = installmentId; }
}