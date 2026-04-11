package com.iscms.dao;

import com.iscms.model.Payment;
import java.util.List;

// DAO interface for payment database operations
// Follows the DAO pattern: all SQL stays in the implementation, not in the service layer
public interface PaymentDAO {

    // Insert a new payment record into the database
    // Called when a manager approves a registration or tier upgrade request
    void insert(Payment payment);

    // Return all payment records for a given member
    // Used in member dashboard to show payment history (UC-M12)
    List<Payment> findByMemberId(int memberId);

    // Return all payment records across all members
    // Used in admin dashboard and revenue reports (UC-A10)
    List<Payment> findAll();
}