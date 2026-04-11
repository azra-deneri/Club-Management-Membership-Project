package com.iscms.service;

import com.iscms.dao.MemberDAO;
import com.iscms.dao.MemberDAOImpl;
import com.iscms.dao.PaymentDAO;
import com.iscms.dao.PaymentDAOImpl;
import com.iscms.model.Member;
import com.iscms.model.Payment;

import java.util.List;

// Service class responsible for generating reports
// Used in manager dashboard Reports tab
// Provides data for: Active Members, Expiring Soon, BMI Distribution, Monthly Revenue
public class ReportService {

    private final MemberDAO memberDAO;
    private final PaymentDAO paymentDAO;

    // Threshold in days for "expiring soon" membership filter
    // Members whose membership ends within this many days are considered expiring soon
    public static final int EXPIRING_SOON_DAYS = 30;

    // Default constructor — creates concrete DAO implementations
    public ReportService() {
        this.memberDAO  = new MemberDAOImpl();
        this.paymentDAO = new PaymentDAOImpl();
    }

    // Constructor for unit testing — allows injecting mock DAO objects
    public ReportService(MemberDAO memberDAO, PaymentDAO paymentDAO) {
        this.memberDAO  = memberDAO;
        this.paymentDAO = paymentDAO;
    }

    // Returns all members with ACTIVE status
    // Used in Active Members report tab
    public List<Member> getActiveMembers() {
        return memberDAO.findByStatus("ACTIVE");
    }

    // Returns all payment records across all members
    // Used in Monthly Revenue report tab
    public List<Payment> getAllPayments() {
        return paymentDAO.findAll();
    }
}