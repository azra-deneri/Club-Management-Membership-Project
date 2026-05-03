package com.iscms.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Represents a single scheduled monthly payment of an ANNUAL_INSTALLMENT membership.
// Twelve installments are created when an ANNUAL_INSTALLMENT membership is activated.
// The first installment is created already PAID (it covers the activation payment);
// the remaining 11 are created PENDING and become OVERDUE if not paid by their due date.
public class Installment {

    private int           installmentId;
    private int           membershipId;
    private Integer       memberId;          // nullable — set NULL on member deletion
    private int           installmentNo;     // 1..12
    private LocalDate     dueDate;
    private BigDecimal    amount;
    private String        status;            // PAID | PENDING | OVERDUE
    private LocalDateTime paidDate;
    private Integer       paymentId;         // FK to payment that cleared this installment

    // === Getters ===

    public int           getInstallmentId() { return installmentId; }
    public int           getMembershipId()  { return membershipId; }
    public Integer       getMemberId()      { return memberId; }
    public int           getInstallmentNo() { return installmentNo; }
    public LocalDate     getDueDate()       { return dueDate; }
    public BigDecimal    getAmount()        { return amount; }
    public String        getStatus()        { return status; }
    public LocalDateTime getPaidDate()      { return paidDate; }
    public Integer       getPaymentId()     { return paymentId; }

    // === Setters ===

    public void setInstallmentId(int installmentId)         { this.installmentId = installmentId; }
    public void setMembershipId(int membershipId)           { this.membershipId = membershipId; }
    public void setMemberId(Integer memberId)               { this.memberId = memberId; }
    public void setInstallmentNo(int installmentNo)         { this.installmentNo = installmentNo; }
    public void setDueDate(LocalDate dueDate)               { this.dueDate = dueDate; }
    public void setAmount(BigDecimal amount)                { this.amount = amount; }
    public void setStatus(String status)                    { this.status = status; }
    public void setPaidDate(LocalDateTime paidDate)         { this.paidDate = paidDate; }
    public void setPaymentId(Integer paymentId)             { this.paymentId = paymentId; }

    // === Convenience ===

    // Returns true if this installment is past due and not yet paid
    public boolean isOverdue() {
        return "OVERDUE".equals(status) ||
                ("PENDING".equals(status) && dueDate.isBefore(LocalDate.now()));
    }

    public boolean isPaid()    { return "PAID".equals(status); }
    public boolean isPending() { return "PENDING".equals(status); }
}