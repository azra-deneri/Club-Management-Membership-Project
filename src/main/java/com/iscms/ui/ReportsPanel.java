package com.iscms.ui;

import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.model.Payment;
import com.iscms.service.MemberService;
import com.iscms.service.ReportService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// Reports panel shown in Manager Dashboard Reports tab
// Provides 4 report tabs: Active Members, Expiring Soon, BMI Distribution, Monthly Revenue
public class ReportsPanel extends JPanel {

    // Service instances — no DAOs directly in UI layer
    private final ReportService reportService = new ReportService();
    private final MemberService memberService = new MemberService();

    public ReportsPanel() {
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Active Members",   buildActiveMembersReport());
        tabs.addTab("Expiring Soon",    buildExpiringSoonReport());
        tabs.addTab("BMI Distribution", buildBmiReport());
        tabs.addTab("Monthly Revenue",  buildRevenueReport());
        add(tabs, BorderLayout.CENTER);
    }

    // Builds the active members report
    // Shows all ACTIVE members with their current membership tier, package, and days remaining
    // Uses Runnable pattern to avoid duplicating load logic between initial load and refresh
    private JPanel buildActiveMembersReport() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"ID", "Full Name", "Phone", "Tier", "Package", "End Date", "Days Left"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        // Runnable reused for both initial load and refresh button
        Runnable load = () -> {
            model.setRowCount(0);
            for (Member m : reportService.getActiveMembers()) {
                Optional<Membership> ms = memberService.getActiveMembership(m.getMemberId());
                if (ms.isEmpty()) continue;
                long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ms.get().getEndDate());
                model.addRow(new Object[]{
                        m.getMemberId(), m.getFullName(), m.getPhone(),
                        ms.get().getTier(), ms.get().getPackageType(),
                        ms.get().getEndDate(), daysLeft
                });
            }
        };
        load.run();

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> load.run());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(btnRefresh);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        return panel;
    }

    // Builds the expiring soon report
    // Shows only members whose membership ends within EXPIRING_SOON_DAYS (30 days)
    private JPanel buildExpiringSoonReport() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"ID", "Full Name", "Phone", "Tier", "End Date", "Days Left"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        for (Member m : reportService.getActiveMembers()) {
            Optional<Membership> ms = memberService.getActiveMembership(m.getMemberId());
            if (ms.isEmpty()) continue;
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ms.get().getEndDate());
            // Only include members expiring within the threshold defined in ReportService
            if (daysLeft <= ReportService.EXPIRING_SOON_DAYS) {
                model.addRow(new Object[]{
                        m.getMemberId(), m.getFullName(), m.getPhone(),
                        ms.get().getTier(), ms.get().getEndDate(), daysLeft
                });
            }
        }

        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        return panel;
    }

    // Builds the BMI distribution report
    // Only includes members who have a calculated BMI value (weight and height provided)
    private JPanel buildBmiReport() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"ID", "Full Name", "BMI", "Category"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        for (Member m : reportService.getActiveMembers()) {
            // Skip members who have not provided weight and height yet
            if (m.getBmiValue() != null) {
                model.addRow(new Object[]{
                        m.getMemberId(), m.getFullName(),
                        String.format("%.2f", m.getBmiValue()),
                        m.getBmiCategory()
                });
            }
        }

        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        return panel;
    }

    // Builds the monthly revenue report
    // Shows all payment records and calculates total revenue from PAID payments only
    private JPanel buildRevenueReport() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"Payment ID", "Member ID", "Amount", "Date", "Type", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        double total = 0;
        for (Payment p : reportService.getAllPayments()) {
            model.addRow(new Object[]{
                    p.getPaymentId(), p.getMemberId(),
                    String.format("%.2f TL", p.getAmount()),
                    p.getPaymentDate().toLocalDate(),
                    p.getPaymentType(), p.getStatus()
            });
            // Only sum PAID payments — PENDING payments excluded from total
            if ("PAID".equals(p.getStatus())) total += p.getAmount();
        }

        // Total revenue label shown above the table
        JLabel lblTotal = new JLabel("Total Revenue: " + String.format("%.2f TL", total));
        lblTotal.setFont(new Font("Arial", Font.BOLD, 13));
        lblTotal.setForeground(new Color(33, 87, 141));
        lblTotal.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        panel.add(lblTotal, BorderLayout.NORTH);
        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        return panel;
    }
}