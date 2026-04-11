package com.iscms.ui;

import com.iscms.service.MemberService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

// Reusable panel showing a comparison table of membership tier benefits and pricing
// Used in RegisterFrame (side panel) and MemberDashboard membership tab
public class TierBenefitsPanel extends JPanel {

    public TierBenefitsPanel() {
        MemberService memberService = new MemberService();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Membership Tier Benefits"));

        // Prices are fetched from MemberService — stays consistent with business logic
        // If prices change in the service, this panel updates automatically
        double classicMonthly    = memberService.calculateAmount("CLASSIC", "MONTHLY");
        double goldMonthly       = memberService.calculateAmount("GOLD",    "MONTHLY");
        double vipMonthly        = memberService.calculateAmount("VIP",     "MONTHLY");
        double classicPrepaid    = memberService.calculateAmount("CLASSIC", "ANNUAL_PREPAID");
        double goldPrepaid       = memberService.calculateAmount("GOLD",    "ANNUAL_PREPAID");
        double vipPrepaid        = memberService.calculateAmount("VIP",     "ANNUAL_PREPAID");
        double classicInstalment = memberService.calculateAmount("CLASSIC", "ANNUAL_INSTALLMENT");
        double goldInstalment    = memberService.calculateAmount("GOLD",    "ANNUAL_INSTALLMENT");
        double vipInstalment     = memberService.calculateAmount("VIP",     "ANNUAL_INSTALLMENT");

        // Benefit comparison table — rows define features, columns define tiers
        String[] cols = {"Feature", "CLASSIC", "GOLD", "VIP"};
        Object[][] data = {
                {"Monthly Fee",
                        String.format("%.0f TL", classicMonthly),
                        String.format("%.0f TL", goldMonthly),
                        String.format("%.0f TL", vipMonthly)},
                {"Annual Prepaid (–15%)",
                        String.format("%.0f TL", classicPrepaid),
                        String.format("%.0f TL", goldPrepaid),
                        String.format("%.0f TL", vipPrepaid)},
                {"Annual Installment (+7%)",
                        String.format("%.0f TL/mo", classicInstalment),
                        String.format("%.0f TL/mo", goldInstalment),
                        String.format("%.0f TL/mo", vipInstalment)},
                {"Gym Access",          "✓",          "✓",          "✓"},
                {"Pool Access",         "✗",          "✓",          "✓"},
                {"Group Classes",       "✗",          "✓",          "✓"},
                {"Personal Trainer",    "✗",          "2/month",    "4/month"},
                {"Sauna & Steam Room",  "✗",          "✗",          "✓"},
                {"Beverage Bar Credit", "✗",          "100 TL/mo",  "250 TL/mo"},
                {"Membership Freeze",   "1x/period",  "2x/period",  "3x/period"},
        };

        // Non-editable table model
        DefaultTableModel model = new DefaultTableModel(data, cols) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        // Give more space to the feature name column
        table.getColumnModel().getColumn(0).setPreferredWidth(180);

        add(new JScrollPane(table), BorderLayout.CENTER);
    }
}