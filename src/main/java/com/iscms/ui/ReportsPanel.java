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
// Member/Payment IDs are kept in the underlying model for data integrity but hidden
// from the view — operational dashboards don't need to expose internal identifiers.
public class ReportsPanel extends JPanel {

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

    // Active members — full name / phone / tier / package / end date / days left
    private JPanel buildActiveMembersReport() {
        JPanel panel = new JPanel(new BorderLayout());

        // ID kept at model column 0 (hidden in view) so future actions can still reference it
        String[] cols = {"ID", "Full Name", "Phone", "Tier", "Package", "End Date", "Days Left"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

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

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> load.run());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(btnRefresh);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // Expiring soon — members whose membership ends within EXPIRING_SOON_DAYS (30 days)
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
            if (daysLeft <= ReportService.EXPIRING_SOON_DAYS) {
                model.addRow(new Object[]{
                        m.getMemberId(), m.getFullName(), m.getPhone(),
                        ms.get().getTier(), ms.get().getEndDate(), daysLeft
                });
            }
        }

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // BMI distribution — only members who have a calculated BMI (weight + height provided)
    private JPanel buildBmiReport() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"ID", "Full Name", "BMI", "Category"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        for (Member m : reportService.getActiveMembers()) {
            if (m.getBmiValue() != null) {
                model.addRow(new Object[]{
                        m.getMemberId(), m.getFullName(),
                        String.format("%.2f", m.getBmiValue()),
                        m.getBmiCategory()
                });
            }
        }

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // Monthly revenue — all payments + total of PAID payments only
    // Member ID column dropped entirely (no manager workflow needs it from this view)
    // Payment ID kept at model col 0, hidden in view
    private JPanel buildRevenueReport() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] cols = {"PaymentID", "Amount", "Date", "Type", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        double total = 0;
        for (Payment p : reportService.getAllPayments()) {
            model.addRow(new Object[]{
                    p.getPaymentId(),
                    String.format("%.2f TL", p.getAmount()),
                    p.getPaymentDate().toLocalDate(),
                    p.getPaymentType(), p.getStatus()
            });
            if ("PAID".equals(p.getStatus())) total += p.getAmount();
        }

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));

        JLabel lblTotal = new JLabel("Total Revenue: " + String.format("%.2f TL", total));
        lblTotal.setFont(new Font("Arial", Font.BOLD, 13));
        lblTotal.setForeground(new Color(33, 87, 141));
        lblTotal.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        panel.add(lblTotal, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }
}