package com.iscms.service;

import com.iscms.dao.MemberDAO;
import com.iscms.dao.MemberDAOImpl;
import com.iscms.dao.PaymentDAO;
import com.iscms.dao.PaymentDAOImpl;
import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.model.Payment;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Service class responsible for generating reports
// Used in manager dashboard Reports tab
// Provides data for: Active Members, Expiring Soon, BMI Distribution, Monthly Revenue
//
// NOTE: As part of Project 2 web migration, the row-building logic that was
// previously inside ReportsPanel (Swing) was moved here. This keeps business
// logic in the service layer; controllers and panels now just render.
public class ReportService {

    private final MemberDAO memberDAO;
    private final PaymentDAO paymentDAO;

    // Lazily-initialized to avoid circular-construction issues; we only need it
    // for membership lookups when building row DTOs.
    private MemberService memberService;

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

    // Setter-injection for MemberService so tests can mock it.
    // In production this is created on first use.
    public void setMemberService(MemberService memberService) {
        this.memberService = memberService;
    }

    private MemberService memberService() {
        if (memberService == null) memberService = new MemberService();
        return memberService;
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

    // === Project 2: report row DTOs ===

    // One row in the Active Members report.
    public record ActiveMemberRow(int memberId, String fullName, String phone,
                                  String tier, String packageType,
                                  LocalDate endDate, long daysLeft) {}

    // One row in the Expiring Soon report (subset of ActiveMemberRow).
    public record ExpiringMemberRow(int memberId, String fullName, String phone,
                                    String tier, LocalDate endDate, long daysLeft) {}

    // One row in the BMI Distribution report.
    public record BmiRow(int memberId, String fullName, double bmi, String category) {}

    // One row in the Monthly Revenue payments table.
    public record PaymentRow(int paymentId, double amount, LocalDate date,
                             String type, String status) {}

    // === Project 2: report-building methods ===

    // Builds the Active Members report rows. Active members without an active
    // membership are skipped (defensive — should not happen, but guards bad data).
    public List<ActiveMemberRow> buildActiveMembersRows() {
        List<ActiveMemberRow> rows = new ArrayList<>();
        for (Member m : getActiveMembers()) {
            Optional<Membership> ms = memberService().getActiveMembership(m.getMemberId());
            if (ms.isEmpty()) continue;
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ms.get().getEndDate());
            rows.add(new ActiveMemberRow(
                    m.getMemberId(), m.getFullName(), m.getPhone(),
                    ms.get().getTier(), ms.get().getPackageType(),
                    ms.get().getEndDate(), daysLeft
            ));
        }
        return rows;
    }

    // Builds the Expiring Soon report rows — daysLeft <= EXPIRING_SOON_DAYS.
    public List<ExpiringMemberRow> buildExpiringSoonRows() {
        List<ExpiringMemberRow> rows = new ArrayList<>();
        for (Member m : getActiveMembers()) {
            Optional<Membership> ms = memberService().getActiveMembership(m.getMemberId());
            if (ms.isEmpty()) continue;
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ms.get().getEndDate());
            if (daysLeft <= EXPIRING_SOON_DAYS) {
                rows.add(new ExpiringMemberRow(
                        m.getMemberId(), m.getFullName(), m.getPhone(),
                        ms.get().getTier(), ms.get().getEndDate(), daysLeft
                ));
            }
        }
        return rows;
    }

    // Builds BMI distribution rows — only members with a recorded BMI value.
    public List<BmiRow> buildBmiRows() {
        List<BmiRow> rows = new ArrayList<>();
        for (Member m : getActiveMembers()) {
            if (m.getBmiValue() != null) {
                rows.add(new BmiRow(
                        m.getMemberId(), m.getFullName(),
                        m.getBmiValue(), m.getBmiCategory()
                ));
            }
        }
        return rows;
    }

    // Aggregated BMI category counts for chart rendering.
    public record BmiCategoryCount(String category, int count) {}

    // Builds the BMI category distribution — used to render a category bar/pie chart.
    public List<BmiCategoryCount> buildBmiCategoryDistribution() {
        int under = 0, normal = 0, over = 0, obese = 0, other = 0;
        for (BmiRow r : buildBmiRows()) {
            String c = r.category() == null ? "" : r.category().toUpperCase();
            if (c.contains("UNDER"))      under++;
            else if (c.contains("NORMAL")) normal++;
            else if (c.contains("OBES"))   obese++;
            else if (c.contains("OVER"))   over++;
            else                            other++;
        }
        List<BmiCategoryCount> out = new ArrayList<>();
        out.add(new BmiCategoryCount("Underweight", under));
        out.add(new BmiCategoryCount("Normal",      normal));
        out.add(new BmiCategoryCount("Overweight",  over));
        out.add(new BmiCategoryCount("Obese",       obese));
        if (other > 0) out.add(new BmiCategoryCount("Other", other));
        return out;
    }

    // Builds all-payments report rows.
    public List<PaymentRow> buildPaymentRows() {
        List<PaymentRow> rows = new ArrayList<>();
        for (Payment p : getAllPayments()) {
            rows.add(new PaymentRow(
                    p.getPaymentId(), p.getAmount(),
                    p.getPaymentDate().toLocalDate(),
                    p.getPaymentType(), p.getStatus()
            ));
        }
        return rows;
    }

    // Total revenue across all PAID payments. UNPAID/OVERDUE are excluded since
    // they have not been collected yet.
    public double getTotalPaidRevenue() {
        double total = 0;
        for (Payment p : getAllPayments()) {
            if ("PAID".equals(p.getStatus())) total += p.getAmount();
        }
        return total;
    }

    // Monthly revenue series — last N months including the current month.
    // Returned in chronological order (oldest → newest) so charts render naturally.
    public record MonthlyRevenuePoint(int year, int month, double total) {}

    public List<MonthlyRevenuePoint> getMonthlyRevenue(int months) {
        if (months < 1) months = 1;

        LocalDate today = LocalDate.now();
        // Initialize result with zero totals for every month in the window.
        double[] totals = new double[months];
        int[] years    = new int[months];
        int[] monthsA  = new int[months];
        for (int i = 0; i < months; i++) {
            LocalDate d = today.minusMonths(months - 1 - i);
            years[i]   = d.getYear();
            monthsA[i] = d.getMonthValue();
        }

        for (Payment p : getAllPayments()) {
            if (!"PAID".equals(p.getStatus())) continue;
            LocalDate d = p.getPaymentDate().toLocalDate();
            for (int i = 0; i < months; i++) {
                if (d.getYear() == years[i] && d.getMonthValue() == monthsA[i]) {
                    totals[i] += p.getAmount();
                    break;
                }
            }
        }

        List<MonthlyRevenuePoint> out = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            out.add(new MonthlyRevenuePoint(years[i], monthsA[i], totals[i]));
        }
        return out;
    }
}