package com.iscms;

import com.iscms.dao.MemberDAO;
import com.iscms.dao.PaymentDAO;
import com.iscms.model.Member;
import com.iscms.model.Payment;
import com.iscms.service.ReportService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Unit tests for ReportService — covers active member and payment report queries
// Uses Mockito to mock DAO interfaces — no real DB calls
@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    // Mocked DAO interfaces — injected into ReportService via constructor
    @Mock MemberDAO memberDAO;
    @Mock PaymentDAO paymentDAO;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(memberDAO, paymentDAO);
    }

    // getActiveMembers must return the list of ACTIVE members from DAO
    @Test
    void getActiveMembers_returnsActiveMembers() {
        Member m = new Member();
        when(memberDAO.findByStatus("ACTIVE")).thenReturn(List.of(m));

        List<Member> result = reportService.getActiveMembers();

        assertEquals(1, result.size());
        verify(memberDAO).findByStatus("ACTIVE");
    }

    // getActiveMembers must return empty list when no ACTIVE members exist
    @Test
    void getActiveMembers_returnsEmptyList_whenNoActiveMembers() {
        when(memberDAO.findByStatus("ACTIVE")).thenReturn(List.of());

        List<Member> result = reportService.getActiveMembers();

        assertTrue(result.isEmpty());
        verify(memberDAO).findByStatus("ACTIVE");
    }

    // getAllPayments must return all payment records from DAO
    @Test
    void getAllPayments_returnsAllPayments() {
        Payment p = new Payment();
        when(paymentDAO.findAll()).thenReturn(List.of(p));

        List<Payment> result = reportService.getAllPayments();

        assertEquals(1, result.size());
        verify(paymentDAO).findAll();
    }

    // getAllPayments must return empty list when no payments exist
    @Test
    void getAllPayments_returnsEmptyList_whenNoPayments() {
        when(paymentDAO.findAll()).thenReturn(List.of());

        List<Payment> result = reportService.getAllPayments();

        assertTrue(result.isEmpty());
        verify(paymentDAO).findAll();
    }

    // EXPIRING_SOON_DAYS constant must be 30 — used in ReportsPanel to filter expiring memberships
    @Test
    void expiringSoonDays_is30() {
        assertEquals(30, ReportService.EXPIRING_SOON_DAYS);
    }
}