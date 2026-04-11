package com.iscms.ui;

import com.iscms.model.*;
import com.iscms.service.MemberService;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Main dashboard for logged-in members
// Provides 6 tabs: Profile, Membership, Events, PT Sessions, BMI, Payments
public class MemberDashboard extends JFrame {

    // The currently logged-in member
    private final Member member;

    // Service instance — no DAOs directly in UI layer
    private final MemberService memberService = new MemberService();

    public MemberDashboard(Member member) {
        this.member = member;
        setTitle("Member Panel — " + member.getFullName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        initUI();
    }

    // Builds the main layout: top bar + tabbed pane with 6 tabs
    private void initUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(33, 120, 80));
        topBar.setPreferredSize(new Dimension(0, 50));

        JLabel lblTitle = new JLabel("  ISC-MS | Member: " + member.getFullName());
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 14));
        topBar.add(lblTitle, BorderLayout.WEST);

        // Logout button — closes dashboard and opens login screen
        JButton btnLogout = new JButton("Logout");
        btnLogout.setBackground(new Color(180, 50, 50));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setOpaque(true);
        btnLogout.setBorderPainted(false);
        btnLogout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        right.setOpaque(false);
        right.add(btnLogout);
        topBar.add(right, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // Six tabs — Events, PT Sessions, BMI reuse dedicated panel classes
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Profile",     buildProfilePanel());
        tabs.addTab("Membership",  buildMembershipPanel());
        tabs.addTab("Events",      new MemberEventPanel(member));
        tabs.addTab("PT Sessions", new PTPanel(member));
        tabs.addTab("BMI",         new BmiPanel(member));
        tabs.addTab("Payments",    buildPaymentsPanel());
        add(tabs, BorderLayout.CENTER);
    }

    // --- Profile Tab ---

    // Builds the profile edit form
    // Read-only: full name, date of birth
    // Editable: phone, email, weight, height, emergency contact
    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Read-only fields shown with grey background
        JTextField txtName   = readOnly(member.getFullName());
        JTextField txtPhone  = new JTextField(member.getPhone() != null ? member.getPhone() : "");
        JTextField txtEmail  = new JTextField(member.getEmail() != null ? member.getEmail() : "");
        JTextField txtDob    = readOnly(member.getDateOfBirth().toString());
        JTextField txtWeight = new JTextField(member.getWeight() != null ? String.valueOf(member.getWeight()) : "");
        JTextField txtHeight = new JTextField(member.getHeight() != null ? String.valueOf(member.getHeight()) : "");
        JTextField txtEcName  = new JTextField(member.getEmergencyContactName() != null ? member.getEmergencyContactName() : "");
        JTextField txtEcPhone = new JTextField(member.getEmergencyContactPhone() != null ? member.getEmergencyContactPhone() : "");

        String[] labels    = {"Full Name", "Phone", "Email", "Date of Birth",
                "Weight (kg)", "Height (cm)", "Emergency Contact", "Emergency Phone"};
        Component[] fields = {txtName, txtPhone, txtEmail, txtDob,
                txtWeight, txtHeight, txtEcName, txtEcPhone};

        for (int i = 0; i < fields.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.3;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.7;
            panel.add(fields[i], c);
        }

        JButton btnSave = new JButton("Save Changes");
        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridx = 0; c.gridy = fields.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        btnSave.addActionListener(e -> {
            try {
                String newPhone = txtPhone.getText().trim();
                String newEmail = txtEmail.getText().trim();

                // Validation: phone must not be empty
                if (newPhone.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Phone number cannot be empty.");
                    return;
                }

                // Validation: phone must be exactly 10 digits
                if (newPhone.length() != 10 || !newPhone.matches("\\d+")) {
                    JOptionPane.showMessageDialog(panel,
                            "Phone must be exactly 10 digits.",
                            "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Nullable doubles — empty field means null (not provided)
                Double weight = txtWeight.getText().isBlank() ? null
                        : Double.parseDouble(txtWeight.getText().trim());
                Double height = txtHeight.getText().isBlank() ? null
                        : Double.parseDouble(txtHeight.getText().trim());

                // Update weight, height, emergency contact via service
                // BMI is automatically recalculated if both weight and height are provided
                memberService.updateMemberProfile(member.getMemberId(),
                        weight, height,
                        txtEcName.getText().trim(),
                        txtEcPhone.getText().trim());

                // Update phone only if it changed — uniqueness check handled in service
                if (!newPhone.equals(member.getPhone())) {
                    memberService.updatePhone(member.getMemberId(), newPhone);
                    member.setPhone(newPhone);
                }

                // Update email only if it changed — uniqueness check handled in service
                if (!newEmail.equals(member.getEmail() != null ? member.getEmail() : "")) {
                    memberService.updateEmail(member.getMemberId(), newEmail);
                    member.setEmail(newEmail);
                }

                // Update local member object to reflect saved values
                member.setWeight(weight);
                member.setHeight(height);
                member.setEmergencyContactName(txtEcName.getText().trim());
                member.setEmergencyContactPhone(txtEcPhone.getText().trim());

                // Refresh BMI tab to show updated values
                refreshBmiTab();
                JOptionPane.showMessageDialog(panel, "Profile updated successfully.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel,
                        "Weight and height must be valid numbers.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    // Refreshes the BMI tab after profile updates
    // Re-fetches member from DB to get the latest BMI values
    private void refreshBmiTab() {
        for (Component comp : getContentPane().getComponents()) {
            if (comp instanceof JTabbedPane tabs) {
                Member refreshed = memberService.getMemberById(member.getMemberId()).orElse(member);
                member.setWeight(refreshed.getWeight());
                member.setHeight(refreshed.getHeight());
                member.setBmiValue(refreshed.getBmiValue());
                member.setBmiCategory(refreshed.getBmiCategory());
                // Replace BMI tab content with a fresh BmiPanel
                tabs.setComponentAt(4, new BmiPanel(member));
                tabs.revalidate();
                tabs.repaint();
                break;
            }
        }
    }

    // --- Membership Tab ---

    // Builds the membership info panel
    // Shows current membership details, freeze count, tier benefits
    // Provides buttons: upgrade tier, freeze, renew (if PASSIVE)
    private JPanel buildMembershipPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        Optional<Membership> msOpt = memberService.getActiveMembership(member.getMemberId());

        // Handle case where no active membership exists
        if (msOpt.isEmpty()) {
            List<Membership> all = memberService.getAllMemberships(member.getMemberId());
            // If member is PASSIVE — show renewal option
            if (!all.isEmpty() && "PASSIVE".equals(member.getStatus())) {
                Membership lastMs = all.getLast();
                JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
                JLabel lblMsg = new JLabel(
                        "Your membership has expired. Please submit a renewal request.",
                        SwingConstants.CENTER);
                lblMsg.setForeground(new Color(180, 50, 50));
                centerPanel.add(lblMsg, BorderLayout.NORTH);

                JButton btnRenew = new JButton("Renew Membership");
                btnRenew.setBackground(new Color(33, 120, 80));
                btnRenew.setForeground(Color.WHITE);
                btnRenew.setOpaque(true);
                btnRenew.setBorderPainted(false);
                btnRenew.addActionListener(e -> showRenewDialog(lastMs.getTier()));

                JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
                btnPanel.add(btnRenew);
                centerPanel.add(btnPanel, BorderLayout.CENTER);
                panel.add(centerPanel, BorderLayout.CENTER);
                return panel;
            }
            panel.add(new JLabel("No active membership found.", SwingConstants.CENTER),
                    BorderLayout.CENTER);
            return panel;
        }

        Membership ms = msOpt.get();
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                LocalDateTime.now().toLocalDate(), ms.getEndDate());

        // Membership info grid
        JPanel info = new JPanel(new GridLayout(0, 2, 10, 8));
        info.setBorder(BorderFactory.createTitledBorder("Current Membership"));
        info.add(new JLabel("Tier:"));           info.add(new JLabel(ms.getTier()));
        info.add(new JLabel("Package:"));        info.add(new JLabel(ms.getPackageType()));
        info.add(new JLabel("Start Date:"));     info.add(new JLabel(ms.getStartDate().toString()));
        info.add(new JLabel("End Date:"));       info.add(new JLabel(ms.getEndDate().toString()));
        info.add(new JLabel("Days Remaining:")); info.add(new JLabel(daysLeft + " days"));
        info.add(new JLabel("Status:"));         info.add(new JLabel(ms.getStatus()));
        info.add(new JLabel("Freeze Used:"));    info.add(new JLabel(ms.getFreezeCount() + "x"));

        panel.add(info, BorderLayout.NORTH);
        panel.add(new TierBenefitsPanel(), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // Tier upgrade button — disabled for VIP (already at highest tier)
        JButton btnUpgrade = new JButton("Request Tier Upgrade");
        btnUpgrade.setBackground(new Color(180, 130, 0));
        btnUpgrade.setForeground(Color.WHITE);
        btnUpgrade.setOpaque(true);
        btnUpgrade.setBorderPainted(false);
        if ("VIP".equals(ms.getTier())) {
            btnUpgrade.setEnabled(false);
            btnUpgrade.setToolTipText("You are already on the highest tier.");
        } else {
            btnUpgrade.addActionListener(e -> showUpgradeDialog(ms, daysLeft));
        }
        btnPanel.add(btnUpgrade);

        // Freeze button — business rules enforced in service
        JButton btnFreeze = new JButton("Freeze Membership");
        btnFreeze.addActionListener(e -> showFreezeDialog(ms));
        btnPanel.add(btnFreeze);

        // Renewal button — only shown for members with PASSIVE status
        if ("PASSIVE".equals(member.getStatus())) {
            JButton btnRenew = new JButton("Renew Membership");
            btnRenew.setBackground(new Color(33, 120, 80));
            btnRenew.setForeground(Color.WHITE);
            btnRenew.setOpaque(true);
            btnRenew.setBorderPainted(false);
            btnRenew.addActionListener(e -> showRenewDialog(ms.getTier()));
            btnPanel.add(btnRenew);
        }

        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // Dialog for requesting a tier upgrade
    // Shows available tiers based on current tier (CLASSIC → GOLD/VIP, GOLD → VIP)
    // Fee is calculated as: (new daily rate - current daily rate) × days remaining
    private void showUpgradeDialog(Membership ms, long daysLeft) {
        String[] availableTiers = "CLASSIC".equals(ms.getTier())
                ? new String[]{"GOLD", "VIP"}
                : new String[]{"VIP"};

        JComboBox<String> cbNewTier = new JComboBox<>(availableTiers);
        JComboBox<String> cbPackage = new JComboBox<>(
                new String[]{"MONTHLY", "ANNUAL_INSTALLMENT", "ANNUAL_PREPAID"});

        JPanel content  = new JPanel(new BorderLayout(0, 10));
        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        infoPanel.add(new JLabel("Current Tier: " + ms.getTier()));

        JPanel tierRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tierRow.add(new JLabel("New Tier:"));
        tierRow.add(cbNewTier);
        infoPanel.add(tierRow);

        JPanel packageRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        packageRow.add(new JLabel("Package:  "));
        packageRow.add(cbPackage);
        infoPanel.add(packageRow);

        JLabel lblFee = new JLabel();
        infoPanel.add(lblFee);
        infoPanel.add(new JLabel("Days Remaining: " + daysLeft));
        infoPanel.add(new JLabel("Request will be sent to manager. Please pay within 3 days."));

        // Recalculates and updates the fee label whenever tier or package changes
        Runnable updateFee = () -> {
            String newTier = cbNewTier.getSelectedItem() != null
                    ? cbNewTier.getSelectedItem().toString() : availableTiers[0];
            String pkg = cbPackage.getSelectedItem() != null
                    ? cbPackage.getSelectedItem().toString() : "MONTHLY";

            double currentDaily = memberService.calculateAmount(ms.getTier(), "MONTHLY") / 30;
            double newDaily = switch (pkg) {
                case "ANNUAL_PREPAID"     -> (memberService.calculateAmount(newTier, "MONTHLY") * 12 * 0.85) / 365;
                case "ANNUAL_INSTALLMENT" -> (memberService.calculateAmount(newTier, "MONTHLY") * 1.07) / 30;
                default                   -> memberService.calculateAmount(newTier, "MONTHLY") / 30;
            };
            double fee = Math.max(0, (newDaily - currentDaily) * daysLeft);
            lblFee.setText(String.format("Upgrade Fee: %.2f TL", fee));
        };
        cbNewTier.addActionListener(e -> updateFee.run());
        cbPackage.addActionListener(e -> updateFee.run());
        updateFee.run();

        content.add(infoPanel, BorderLayout.NORTH);
        content.add(new TierBenefitsPanel(), BorderLayout.CENTER);
        content.setPreferredSize(new Dimension(700, 450));

        int confirm = JOptionPane.showConfirmDialog(this, content,
                "Upgrade Tier", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            String newTier = cbNewTier.getSelectedItem() != null
                    ? cbNewTier.getSelectedItem().toString() : availableTiers[0];
            String pkg = cbPackage.getSelectedItem() != null
                    ? cbPackage.getSelectedItem().toString() : "MONTHLY";

            // Recalculate fee before submitting — consistent with displayed value
            double currentDaily = memberService.calculateAmount(ms.getTier(), "MONTHLY") / 30;
            double newDaily = switch (pkg) {
                case "ANNUAL_PREPAID"     -> (memberService.calculateAmount(newTier, "MONTHLY") * 12 * 0.85) / 365;
                case "ANNUAL_INSTALLMENT" -> (memberService.calculateAmount(newTier, "MONTHLY") * 1.07) / 30;
                default                   -> memberService.calculateAmount(newTier, "MONTHLY") / 30;
            };
            double fee = Math.max(0, (newDaily - currentDaily) * daysLeft);

            try {
                // Submit upgrade request via service — creates DB record with 3-day expiry
                memberService.createTierUpgradeRequest(
                        member.getMemberId(), ms.getMembershipId(),
                        ms.getTier(), newTier, pkg, fee);
                JOptionPane.showMessageDialog(this,
                        "Upgrade request submitted!\n" +
                                "Please pay " + String.format("%.2f TL", fee) +
                                " to the manager within 3 days.",
                        "Request Submitted", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Dialog for freezing the membership
    // Offers 3 freeze duration options: 7, 14, or 30 days
    // Business rules (freeze limit, 3-day expiry window) enforced in service
    private void showFreezeDialog(Membership ms) {
        String[] options = {"7 days", "14 days", "30 days"};
        int choice = JOptionPane.showOptionDialog(this,
                "Select freeze duration:", "Freeze Membership",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice >= 0) {
            int days = new int[]{7, 14, 30}[choice];
            try {
                memberService.freezeMembership(ms.getMembershipId(), days);
                JOptionPane.showMessageDialog(this,
                        "Membership frozen for " + days + " days.");
                refreshMembershipTab();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Refreshes the membership tab after freeze/unfreeze operations
    private void refreshMembershipTab() {
        for (Component comp : getContentPane().getComponents()) {
            if (comp instanceof JTabbedPane tabs) {
                tabs.setComponentAt(1, buildMembershipPanel());
                tabs.revalidate();
                tabs.repaint();
                break;
            }
        }
    }

    // Dialog for submitting a membership renewal request
    // Available tiers depend on current tier — members cannot downgrade
    // Calculates estimated amount dynamically as tier/package selection changes
    private void showRenewDialog(String currentTier) {
        JDialog dialog = new JDialog(this, "Renew Membership", true);
        dialog.setSize(500, 350);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Members cannot renew to a lower tier than their current one
        String[] tiers = switch (currentTier) {
            case "GOLD" -> new String[]{"GOLD", "VIP"};
            case "VIP"  -> new String[]{"VIP"};
            default     -> new String[]{"CLASSIC", "GOLD", "VIP"};
        };

        JComboBox<String> cbTier    = new JComboBox<>(tiers);
        JComboBox<String> cbPackage = new JComboBox<>(
                new String[]{"MONTHLY", "ANNUAL_INSTALLMENT", "ANNUAL_PREPAID"});
        JLabel lblAmount = new JLabel("Amount: 750.00 TL /month");
        lblAmount.setFont(new Font("Arial", Font.BOLD, 12));
        lblAmount.setForeground(new Color(33, 87, 141));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tier *:"));          form.add(cbTier);
        form.add(new JLabel("Package *:"));        form.add(cbPackage);
        form.add(new JLabel("Estimated Amount:")); form.add(lblAmount);
        panel.add(form, BorderLayout.CENTER);

        // Recalculates and updates the amount label whenever tier or package changes
        // Uses existing memberService field — no need for a separate instance
        Runnable updateAmt = () -> {
            String tier = cbTier.getSelectedItem() != null
                    ? cbTier.getSelectedItem().toString() : "CLASSIC";
            String pkg = cbPackage.getSelectedItem() != null
                    ? cbPackage.getSelectedItem().toString() : "MONTHLY";
            double amt = memberService.calculateAmount(tier, pkg);
            String note = switch (pkg) {
                case "ANNUAL_PREPAID"     -> " (15% discount, total)";
                case "ANNUAL_INSTALLMENT" -> " /month (+7%)";
                default                   -> " /month";
            };
            lblAmount.setText(String.format("Amount: %.2f TL%s", amt, note));
        };
        cbTier.addActionListener(e -> updateAmt.run());
        cbPackage.addActionListener(e -> updateAmt.run());
        updateAmt.run();

        JButton btnSubmit = new JButton("Submit Renewal Request");
        btnSubmit.setBackground(new Color(33, 120, 80));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setOpaque(true);
        btnSubmit.setBorderPainted(false);
        panel.add(btnSubmit, BorderLayout.SOUTH);

        btnSubmit.addActionListener(e -> {
            String tier = cbTier.getSelectedItem() != null
                    ? cbTier.getSelectedItem().toString() : "CLASSIC";
            String pkg = cbPackage.getSelectedItem() != null
                    ? cbPackage.getSelectedItem().toString() : "MONTHLY";
            double amt = memberService.calculateAmount(tier, pkg);

            // Build renewal request object and submit via service
            // Service inserts into registration_request with type = RENEWAL
            RegistrationRequest req = new RegistrationRequest();
            req.setMemberId(member.getMemberId());
            req.setType("RENEWAL");
            req.setTier(tier);
            req.setPackageType(pkg);
            req.setAmount(amt);
            req.setExpiresAt(LocalDateTime.now().plusDays(3));

            try {
                memberService.submitRegistrationRequest(req);
                JOptionPane.showMessageDialog(dialog,
                        "Renewal request submitted! Manager will approve within 3 days.");
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.add(panel);
        dialog.setVisible(true);
    }

    // --- Payments Tab ---

    // Builds the payment history panel — read-only table of all member payments
    private JPanel buildPaymentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"ID", "Amount", "Date", "Type", "Description", "Status"};
        javax.swing.table.DefaultTableModel model =
                new javax.swing.table.DefaultTableModel(cols, 0) {
                    public boolean isCellEditable(int r, int c) { return false; }
                };
        for (Payment p : memberService.getPaymentsByMember(member.getMemberId())) {
            model.addRow(new Object[]{
                    p.getPaymentId(),
                    String.format("%.2f TL", p.getAmount()),
                    p.getPaymentDate().toLocalDate(),
                    p.getPaymentType(),
                    p.getDescription(),
                    p.getStatus()
            });
        }
        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // Helper: creates a non-editable text field with grey background
    private JTextField readOnly(String value) {
        JTextField field = new JTextField(value);
        field.setEditable(false);
        field.setBackground(new Color(240, 240, 240));
        return field;
    }
}