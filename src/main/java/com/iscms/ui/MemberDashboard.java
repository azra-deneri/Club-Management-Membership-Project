package com.iscms.ui;

import com.iscms.model.*;
import com.iscms.service.AuthService;
import com.iscms.service.MemberService;
import com.iscms.service.PaymentResult;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

// Main dashboard for logged-in members.
//
// Tab layout is dynamic:
//   - Always shown: Profile, Membership, Events, PT Sessions, BMI, Payments
//   - Conditionally shown: Installments — only when the member has an active
//     ANNUAL_INSTALLMENT membership OR has historical installment records.
//     Members on MONTHLY / ANNUAL_PREPAID never see this tab.
//
// Profile tab now hosts two account-lifecycle actions:
//   - Cancel Membership: enabled only for ACTIVE members; flips a flag, membership
//     stays active until end_date, then auto-passivates on next login.
//   - Delete Account: hard-delete after password verification and confirmation.
//     FK ON DELETE SET NULL preserves audit records (payments etc).
public class MemberDashboard extends JFrame {

    private final Member member;
    private final MemberService memberService = new MemberService();
    private final AuthService   authService   = new AuthService();

    // Cached on initUI so installment tab visibility decision is consistent
    // across renders, even if the membership status changes mid-session.
    private boolean showInstallmentsTab;

    public MemberDashboard(Member member) {
        this.member = member;
        setTitle("Member Panel — " + member.getFullName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(33, 120, 80));
        topBar.setPreferredSize(new Dimension(0, 50));

        JLabel lblTitle = new JLabel("  ISC-MS | Member: " + member.getFullName());
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 14));
        topBar.add(lblTitle, BorderLayout.WEST);

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

        // Decide once whether to show the Installments tab. Triggers when the member
        // either has an active ANNUAL_INSTALLMENT membership OR any historical
        // installment records (so PASSIVE members can still see their old schedule).
        boolean hasActive = memberService.hasActiveInstallmentMembership(member.getMemberId());
        boolean hasAny    = !memberService.getInstallmentsForMember(member.getMemberId()).isEmpty();
        showInstallmentsTab = hasActive || hasAny;

        // Refresh OVERDUE statuses on dashboard load so the installments table
        // shows accurate badges without waiting for a manager-side sweep.
        memberService.markOverdueInstallments();

        // PAYMENT_HOLD members get a stripped-down dashboard. We only expose the tabs
// they need to clear the hold (Installments + Payments) plus read-only context
// (Profile + Membership). Events / PT Sessions / BMI are hidden — using those
// while you owe money would feel wrong, and it nudges the user toward paying.
        boolean onPaymentHold = "PAYMENT_HOLD".equals(member.getStatus());

        if (onPaymentHold) {
            // Banner directly above the tabs — bright enough to be unmissable
            JPanel banner = new JPanel(new BorderLayout());
            banner.setBackground(new Color(180, 50, 50));
            banner.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            JLabel lblBanner = new JLabel(
                    "⚠  Account on Payment Hold  —  "
                            + "Pay your overdue installments below to restore full access.");
            lblBanner.setForeground(Color.WHITE);
            lblBanner.setFont(new Font("Arial", Font.BOLD, 12));
            banner.add(lblBanner, BorderLayout.WEST);

            // Stack: top bar + banner. We wrap them in a vertical panel so both
            // sit at NORTH of the frame.
            JPanel northStack = new JPanel(new BorderLayout());
            // Re-host the existing topBar (already added). Pull it out and re-add:
            Component existingNorth = ((BorderLayout) getContentPane().getLayout())
                    .getLayoutComponent(BorderLayout.NORTH);
            if (existingNorth != null) {
                getContentPane().remove(existingNorth);
                northStack.add(existingNorth, BorderLayout.NORTH);
            }
            northStack.add(banner, BorderLayout.SOUTH);
            add(northStack, BorderLayout.NORTH);
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Profile",     buildProfilePanel());
        tabs.addTab("Membership",  buildMembershipPanel());

        if (!onPaymentHold) {
            tabs.addTab("Events",      new MemberEventPanel(member));
            tabs.addTab("PT Sessions", new PTPanel(member));
            tabs.addTab("BMI",         new BmiPanel(member));
        }

        tabs.addTab("Payments",    buildPaymentsPanel());
        if (showInstallmentsTab) {
            tabs.addTab("Installments", buildInstallmentsPanel());
        }
        add(tabs, BorderLayout.CENTER);
    }

    // --- Profile Tab ---
    // Includes editable profile fields PLUS account-lifecycle buttons at the bottom.

    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtName    = readOnly(member.getFullName());
        JTextField txtPhone   = new JTextField(member.getPhone() != null ? member.getPhone() : "");
        JTextField txtEmail   = new JTextField(member.getEmail() != null ? member.getEmail() : "");
        JTextField txtDob     = readOnly(member.getDateOfBirth().toString());
        JTextField txtWeight  = new JTextField(member.getWeight() != null ? String.valueOf(member.getWeight()) : "");
        JTextField txtHeight  = new JTextField(member.getHeight() != null ? String.valueOf(member.getHeight()) : "");
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

        // PAYMENT_HOLD: form is read-only. The user's only path forward is paying
        // installments — we don't want them tweaking weight/contact info while
        // the account is in a degraded state.
        boolean onPaymentHold = "PAYMENT_HOLD".equals(member.getStatus());
        if (onPaymentHold) {
            txtPhone.setEditable(false);
            txtEmail.setEditable(false);
            txtWeight.setEditable(false);
            txtHeight.setEditable(false);
            txtEcName.setEditable(false);
            txtEcPhone.setEditable(false);
            btnSave.setEnabled(false);
            btnSave.setText("Profile editing disabled while on Payment Hold");
        }

        c.gridx = 0; c.gridy = fields.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        btnSave.addActionListener(e -> {
            try {
                String newPhone = txtPhone.getText().trim();
                String newEmail = txtEmail.getText().trim();

                if (newPhone.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Phone number cannot be empty.");
                    return;
                }
                if (newPhone.length() != 10 || !newPhone.matches("\\d+")) {
                    JOptionPane.showMessageDialog(panel,
                            "Phone must be exactly 10 digits.",
                            "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                Double weight = txtWeight.getText().isBlank() ? null
                        : Double.parseDouble(txtWeight.getText().trim());
                Double height = txtHeight.getText().isBlank() ? null
                        : Double.parseDouble(txtHeight.getText().trim());

                memberService.updateMemberProfile(member.getMemberId(),
                        weight, height,
                        txtEcName.getText().trim(),
                        txtEcPhone.getText().trim());

                if (!newPhone.equals(member.getPhone())) {
                    memberService.updatePhone(member.getMemberId(), newPhone);
                    member.setPhone(newPhone);
                }

                if (!newEmail.equals(member.getEmail() != null ? member.getEmail() : "")) {
                    memberService.updateEmail(member.getMemberId(), newEmail);
                    member.setEmail(newEmail);
                }

                member.setWeight(weight);
                member.setHeight(height);
                member.setEmergencyContactName(txtEcName.getText().trim());
                member.setEmergencyContactPhone(txtEcPhone.getText().trim());

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

        // === Account lifecycle buttons (Batch 4) ===
        // Hide the Account section entirely when on PAYMENT_HOLD — the user's only
        // task right now is paying their dues; cancellation / deletion is a confusing
        // side door we shouldn't offer until the account is back to ACTIVE.
        if (!onPaymentHold) {
            JSeparator sep = new JSeparator();
            c.gridx = 0; c.gridy = fields.length + 1; c.gridwidth = 2;
            c.insets = new Insets(20, 6, 6, 6);
            panel.add(sep, c);

            JLabel lblSection = new JLabel("Account");
            lblSection.setFont(new Font("Arial", Font.BOLD, 13));
            c.gridy = fields.length + 2;
            c.insets = new Insets(0, 6, 6, 6);
            panel.add(lblSection, c);

            // --- Cancel Membership button ---
            // Active member with no pending cancellation: button enabled
            // Active member who already requested cancellation: shows confirmation label
            // PASSIVE/FROZEN/SUSPENDED member: cancel button hidden (already non-active)
            c.gridy = fields.length + 3;
            if ("ACTIVE".equals(member.getStatus())) {
                if (member.isCancellationRequested()) {
                    // Already cancelled — show informational label, no button
                    JLabel lblCancelled = new JLabel(
                            "✓ Cancellation requested on "
                                    + member.getCancellationRequestedAt()
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    + ". Membership remains active until its end date.");
                    lblCancelled.setForeground(new Color(180, 130, 0));
                    panel.add(lblCancelled, c);
                } else {
                    JButton btnCancel = new JButton("Cancel Membership");
                    btnCancel.setBackground(new Color(180, 130, 0));
                    btnCancel.setForeground(Color.WHITE);
                    btnCancel.setOpaque(true);
                    btnCancel.setBorderPainted(false);
                    btnCancel.addActionListener(e -> handleCancelMembership());
                    panel.add(btnCancel, c);
                }
            }

            // --- Delete Account button ---
            // Always visible (within Account section) — gives every member the option
            // to leave the system entirely.
            c.gridy = fields.length + 4;
            JButton btnDelete = new JButton("Delete Account");
            btnDelete.setBackground(new Color(150, 50, 50));
            btnDelete.setForeground(Color.WHITE);
            btnDelete.setOpaque(true);
            btnDelete.setBorderPainted(false);
            btnDelete.addActionListener(e -> handleDeleteAccount());
            panel.add(btnDelete, c);
        }

        return panel;
    }

    // Member-initiated membership cancellation.
    // Membership stays ACTIVE until end_date; only flips a flag here.
    // Login-time logic in AuthService transitions the member to PASSIVE on expiry.
    private void handleCancelMembership() {
        Optional<Membership> ms = memberService.getActiveMembership(member.getMemberId());
        if (ms.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "You don't have an active membership to cancel.",
                    "Nothing to Cancel", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String message =
                "Are you sure you want to cancel your membership?\n\n" +
                        "• Your membership will remain active until " + ms.get().getEndDate() + ".\n" +
                        "• After that date, your account becomes PASSIVE.\n" +
                        "• You can renew anytime within 1 year of expiry.\n" +
                        "• If you don't renew within 1 year, your account will be deleted.\n\n" +
                        "This is reversible — you can renew at any time.";

        int choice = JOptionPane.showConfirmDialog(this, message,
                "Cancel Membership", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        try {
            memberService.cancelMembership(member.getMemberId());
            // Keep local copy in sync so re-rendering shows the "Cancellation Requested" label
            member.setCancellationRequestedAt(LocalDateTime.now());
            JOptionPane.showMessageDialog(this,
                    "Your cancellation has been recorded.\n" +
                            "Your membership remains active until " + ms.get().getEndDate() + ".",
                    "Cancellation Recorded", JOptionPane.INFORMATION_MESSAGE);
            refreshProfileTab();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Member-initiated full account deletion.
    // Requires password verification (defense against accidental clicks /
    // someone using a logged-in computer that isn't theirs) plus an explicit
    // typed confirmation. After deletion, dashboard closes and login screen reopens.
    private void handleDeleteAccount() {
        // Step 1: explain what's about to happen
        int firstConfirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to PERMANENTLY DELETE your account?\n\n" +
                        "• All personal information will be removed.\n" +
                        "• Past payment records will be retained for accounting (anonymously).\n" +
                        "• You can register again later with the same phone number if you wish.\n\n" +
                        "This action CANNOT be undone.",
                "Delete Account?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (firstConfirm != JOptionPane.YES_OPTION) return;

        // Step 2: password verification
        JPasswordField pwd = new JPasswordField(15);
        int pwdConfirm = JOptionPane.showConfirmDialog(this, pwd,
                "Enter your password to confirm",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (pwdConfirm != JOptionPane.OK_OPTION) return;

        String password = new String(pwd.getPassword());
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Password cannot be empty.");
            return;
        }
        if (!authService.verifyMemberPassword(member.getMemberId(), password)) {
            JOptionPane.showMessageDialog(this,
                    "Incorrect password. Account was NOT deleted.",
                    "Authentication Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Step 3: final irreversible step
        int finalConfirm = JOptionPane.showConfirmDialog(this,
                "Last chance. Click Yes to permanently delete your account.\n\n" +
                        "Click No to keep your account.",
                "Final Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (finalConfirm != JOptionPane.YES_OPTION) return;

        try {
            memberService.deleteAccountSelf(member.getMemberId());
            JOptionPane.showMessageDialog(this,
                    "Your account has been deleted. We're sorry to see you go.",
                    "Account Deleted", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            new LoginFrame().setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not delete account: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshBmiTab() {
        for (Component comp : getContentPane().getComponents()) {
            if (comp instanceof JTabbedPane tabs) {
                Member refreshed = memberService.getMemberById(member.getMemberId()).orElse(member);
                member.setWeight(refreshed.getWeight());
                member.setHeight(refreshed.getHeight());
                member.setBmiValue(refreshed.getBmiValue());
                member.setBmiCategory(refreshed.getBmiCategory());
                tabs.setComponentAt(4, new BmiPanel(member));
                tabs.revalidate();
                tabs.repaint();
                break;
            }
        }
    }

    // Rebuilds the Profile tab so the cancellation label appears after a click
    // (without forcing the user to log out and log back in).
    private void refreshProfileTab() {
        for (Component comp : getContentPane().getComponents()) {
            if (comp instanceof JTabbedPane tabs) {
                tabs.setComponentAt(0, buildProfilePanel());
                tabs.revalidate();
                tabs.repaint();
                break;
            }
        }
    }

    // --- Membership Tab ---

    private JPanel buildMembershipPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        Optional<Membership> msOpt = memberService.getActiveMembership(member.getMemberId());

        if (msOpt.isEmpty()) {
            List<Membership> all = memberService.getAllMemberships(member.getMemberId());
            if (!all.isEmpty() && "PASSIVE".equals(member.getStatus())) {
                Membership lastMs = all.getLast();
                JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
                JLabel lblMsg = new JLabel(
                        "Your membership has expired. Please submit a renewal.",
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

        // PAYMENT_HOLD members can't upgrade or freeze. Both actions either cost
// extra money (upgrade fee) or extend the contract (freeze) — neither makes
// sense while the member owes installments. Restrict here, not just in service.
        boolean onPaymentHold = "PAYMENT_HOLD".equals(member.getStatus());

        JButton btnUpgrade = new JButton("Request Tier Upgrade");
        btnUpgrade.setBackground(new Color(180, 130, 0));
        btnUpgrade.setForeground(Color.WHITE);
        btnUpgrade.setOpaque(true);
        btnUpgrade.setBorderPainted(false);
        if ("VIP".equals(ms.getTier())) {
            btnUpgrade.setEnabled(false);
            btnUpgrade.setToolTipText("You are already on the highest tier.");
        } else if (onPaymentHold) {
            btnUpgrade.setEnabled(false);
            btnUpgrade.setToolTipText("Tier upgrades are unavailable while on Payment Hold.");
        } else {
            btnUpgrade.addActionListener(e -> showUpgradeDialog(ms, daysLeft));
        }
        btnPanel.add(btnUpgrade);

        JButton btnFreeze = new JButton("Freeze Membership");
        if (onPaymentHold) {
            btnFreeze.setEnabled(false);
            btnFreeze.setToolTipText("Freeze is unavailable while on Payment Hold.");
        } else {
            btnFreeze.addActionListener(e -> showFreezeDialog(ms));
        }
        btnPanel.add(btnFreeze);

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

    // --- Tier Upgrade Dialog ---

    private void showUpgradeDialog(Membership ms, long daysLeft) {
        String[] availableTiers = "CLASSIC".equals(ms.getTier())
                ? new String[]{"GOLD", "VIP"}
                : new String[]{"VIP"};

        JComboBox<String> cbNewTier = new JComboBox<>(availableTiers);

        JRadioButton rbCash   = new JRadioButton("Cash (pay at the club)", true);
        JRadioButton rbOnline = new JRadioButton("Online (pay by card now)");
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbCash); bg.add(rbOnline);

        JPanel content  = new JPanel(new BorderLayout(0, 10));
        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 0, 4));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));

        infoPanel.add(new JLabel("Current Tier: " + ms.getTier()
                + "  |  Package: " + ms.getPackageType()
                + " (unchanged)"));

        JPanel tierRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tierRow.add(new JLabel("New Tier:"));
        tierRow.add(cbNewTier);
        infoPanel.add(tierRow);

        JLabel lblFee = new JLabel();
        infoPanel.add(lblFee);
        infoPanel.add(new JLabel("Days Remaining: " + daysLeft));

        JPanel payPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        payPanel.setBorder(BorderFactory.createTitledBorder("Payment Method"));
        payPanel.add(rbCash);
        payPanel.add(rbOnline);
        infoPanel.add(payPanel);

        Runnable updateFee = () -> {
            String newTier = cbNewTier.getSelectedItem() != null
                    ? cbNewTier.getSelectedItem().toString() : availableTiers[0];

            double currentDaily = perDayRate(ms.getTier(), ms.getPackageType());
            double newDaily     = perDayRate(newTier,     ms.getPackageType());
            double fee = Math.max(0, (newDaily - currentDaily) * daysLeft);
            lblFee.setText(String.format("Upgrade Fee: %.2f TL", fee));
        };
        cbNewTier.addActionListener(e -> updateFee.run());
        updateFee.run();

        content.add(infoPanel, BorderLayout.NORTH);
        content.add(new TierBenefitsPanel(), BorderLayout.CENTER);
        content.setPreferredSize(new Dimension(700, 520));

        int confirm = JOptionPane.showConfirmDialog(this, content,
                "Upgrade Tier", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        String newTier = cbNewTier.getSelectedItem() != null
                ? cbNewTier.getSelectedItem().toString() : availableTiers[0];

        double currentDaily = perDayRate(ms.getTier(), ms.getPackageType());
        double newDaily     = perDayRate(newTier,     ms.getPackageType());
        double fee = Math.max(0, (newDaily - currentDaily) * daysLeft);

        if (rbOnline.isSelected()) {
            handleOnlineUpgrade(ms, newTier, fee);
        } else {
            handleCashUpgrade(ms, newTier, fee);
        }
    }

    private void handleCashUpgrade(Membership ms, String newTier, double fee) {
        try {
            memberService.createTierUpgradeRequest(
                    member.getMemberId(), ms.getMembershipId(),
                    ms.getTier(), newTier, fee);
            JOptionPane.showMessageDialog(this,
                    "Upgrade request submitted!\n" +
                            "Please pay " + String.format("%.2f TL", fee) +
                            " in cash to the manager within 3 days.",
                    "Request Submitted", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleOnlineUpgrade(Membership ms, String newTier, double fee) {
        String purpose = "Tier Upgrade: " + ms.getTier() + " → " + newTier;
        PaymentResult result = MockPaymentDialog.showAndProcess(this, fee, purpose);

        if (result == PaymentResult.CANCELLED) return;

        if (!result.isSuccess()) {
            JOptionPane.showMessageDialog(this,
                    MockPaymentDialog.describe(result) + "\n\nUpgrade was not applied.",
                    "Payment Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            memberService.selfUpgradeTier(
                    member.getMemberId(), ms.getMembershipId(),
                    ms.getTier(), newTier, fee);
            JOptionPane.showMessageDialog(this,
                    "Tier upgraded successfully!\n\n" +
                            "Your membership is now " + newTier + ".",
                    "Upgrade Complete", JOptionPane.INFORMATION_MESSAGE);
            refreshMembershipTab();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Payment was processed but the upgrade failed:\n" + ex.getMessage()
                            + "\n\nPlease contact the club for assistance.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private double perDayRate(String tier, String packageType) {
        return switch (packageType) {
            case "ANNUAL_PREPAID"     -> (memberService.calculateAmount(tier, "MONTHLY") * 12 * 0.85) / 365;
            case "ANNUAL_INSTALLMENT" -> (memberService.calculateAmount(tier, "MONTHLY") * 1.07) / 30;
            default                   -> memberService.calculateAmount(tier, "MONTHLY") / 30;
        };
    }

    // --- Freeze Dialog ---

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

    // --- Renewal Dialog ---

    private void showRenewDialog(String currentTier) {
        JDialog dialog = new JDialog(this, "Renew Membership", true);
        dialog.setSize(540, 460);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        String[] tiers = switch (currentTier) {
            case "GOLD" -> new String[]{"GOLD", "VIP"};
            case "VIP"  -> new String[]{"VIP"};
            default     -> new String[]{"CLASSIC", "GOLD", "VIP"};
        };

        JComboBox<String> cbTier    = new JComboBox<>(tiers);
        JComboBox<String> cbPackage = new JComboBox<>();

        JRadioButton rbCash   = new JRadioButton("Cash (pay at the club)", true);
        JRadioButton rbOnline = new JRadioButton("Online (pay by card now)");
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbCash); bg.add(rbOnline);

        JLabel lblAmount = new JLabel("Amount: 750.00 TL /month");
        lblAmount.setFont(new Font("Arial", Font.BOLD, 12));
        lblAmount.setForeground(new Color(33, 87, 141));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Tier *:"));           form.add(cbTier);
        form.add(new JLabel("Package *:"));        form.add(cbPackage);
        form.add(new JLabel("Estimated Amount:")); form.add(lblAmount);

        JPanel payPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        payPanel.setBorder(BorderFactory.createTitledBorder("Payment Method"));
        payPanel.add(rbCash);
        payPanel.add(rbOnline);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.add(form, BorderLayout.NORTH);
        center.add(payPanel, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        Runnable refreshPackages = () -> {
            String prev = (String) cbPackage.getSelectedItem();
            cbPackage.removeAllItems();
            String[] packages = rbOnline.isSelected()
                    ? new String[]{"MONTHLY", "ANNUAL_INSTALLMENT", "ANNUAL_PREPAID"}
                    : new String[]{"MONTHLY", "ANNUAL_PREPAID"};
            for (String pkg : packages) cbPackage.addItem(pkg);
            if (prev != null) {
                for (int i = 0; i < cbPackage.getItemCount(); i++) {
                    if (prev.equals(cbPackage.getItemAt(i))) {
                        cbPackage.setSelectedIndex(i);
                        break;
                    }
                }
            }
        };

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

        cbTier.addActionListener(e    -> updateAmt.run());
        cbPackage.addActionListener(e -> updateAmt.run());
        rbCash.addActionListener(e   -> { refreshPackages.run(); updateAmt.run(); });
        rbOnline.addActionListener(e -> { refreshPackages.run(); updateAmt.run(); });

        refreshPackages.run();
        updateAmt.run();

        JButton btnSubmit = new JButton("Submit Renewal");
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

            if (rbOnline.isSelected()) {
                handleOnlineRenewal(dialog, tier, pkg, amt);
            } else {
                handleCashRenewal(dialog, tier, pkg, amt);
            }
        });

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void handleCashRenewal(JDialog dialog, String tier, String pkg, double amt) {
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
                    "Renewal request submitted!\n" +
                            "Please pay " + String.format("%.2f TL", amt) + " in cash at the club.\n" +
                            "Manager will approve within 3 days.");
            dialog.dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleOnlineRenewal(JDialog dialog, String tier, String pkg, double amt) {
        String purpose = "Renewal: " + tier + " - " + pkg;
        PaymentResult result = MockPaymentDialog.showAndProcess(dialog, amt, purpose);

        if (result == PaymentResult.CANCELLED) return;

        if (!result.isSuccess()) {
            JOptionPane.showMessageDialog(dialog,
                    MockPaymentDialog.describe(result) + "\n\nRenewal was not processed.",
                    "Payment Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            memberService.selfRenewMembership(member.getMemberId(), tier, pkg);
            member.setStatus("ACTIVE");
            JOptionPane.showMessageDialog(dialog,
                    "Membership renewed successfully!\n\n" +
                            "Your " + tier + " - " + pkg + " membership is now active.",
                    "Renewal Complete", JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
            refreshMembershipTab();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(dialog,
                    "Payment was processed but renewal failed:\n" + ex.getMessage()
                            + "\n\nPlease contact the club for assistance.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- Payments Tab ---

    private JPanel buildPaymentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"PaymentID", "Amount", "Date", "Type", "Description", "Method", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Payment p : memberService.getPaymentsByMember(member.getMemberId())) {
            model.addRow(new Object[]{
                    p.getPaymentId(),
                    String.format("%.2f TL", p.getAmount()),
                    p.getPaymentDate().toLocalDate(),
                    p.getPaymentType(),
                    p.getDescription(),
                    p.getPaymentMethod() != null ? p.getPaymentMethod() : "-",
                    p.getStatus()
            });
        }
        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // --- Installments Tab (Batch 4) ---
    // Lists every installment for the member with a "Pay Now" action on
    // PENDING and OVERDUE rows. PAID rows show a checkmark and the date paid.

    private JPanel buildInstallmentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // installment_id at column 0 (hidden in view) so the Pay Now action can read it
        String[] cols = {"InstID", "#", "Due Date", "Amount", "Status", "Paid On"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        // Color OVERDUE rows red so the eye is drawn to them immediately
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(
                    JTable tbl, Object value, boolean isSelected, boolean hasFocus,
                    int row, int col) {
                java.awt.Component comp = super.getTableCellRendererComponent(
                        tbl, value, isSelected, hasFocus, row, col);
                String status = String.valueOf(model.getValueAt(row, 4));
                if (!isSelected) {
                    if ("OVERDUE".equals(status)) {
                        comp.setBackground(new Color(255, 220, 220));
                    } else if ("PAID".equals(status)) {
                        comp.setBackground(new Color(220, 250, 220));
                    } else {
                        comp.setBackground(Color.WHITE);
                    }
                }
                return comp;
            }
        });

        Runnable load = () -> {
            model.setRowCount(0);
            // Refresh OVERDUE flags before reading so display is up to date
            memberService.markOverdueInstallments();
            List<Installment> all = memberService.getInstallmentsForMember(member.getMemberId());
            for (Installment inst : all) {
                model.addRow(new Object[]{
                        inst.getInstallmentId(),
                        inst.getInstallmentNo(),
                        inst.getDueDate(),
                        String.format("%.2f TL", inst.getAmount().doubleValue()),
                        inst.getStatus(),
                        inst.getPaidDate() != null
                                ? inst.getPaidDate().toLocalDate().toString() : "-"
                });
            }
        };
        load.run();

        // Header summary — shows totals at a glance
        JLabel lblSummary = new JLabel();
        lblSummary.setFont(new Font("Arial", Font.BOLD, 12));
        Runnable updateSummary = () -> {
            List<Installment> all = memberService.getInstallmentsForMember(member.getMemberId());
            long paid    = all.stream().filter(i -> "PAID".equals(i.getStatus())).count();
            long overdue = all.stream().filter(i -> "OVERDUE".equals(i.getStatus())).count();
            long pending = all.stream().filter(i -> "PENDING".equals(i.getStatus())).count();
            lblSummary.setText(String.format(
                    "Paid: %d   |   Pending: %d   |   Overdue: %d",
                    paid, pending, overdue));
            lblSummary.setForeground(overdue > 0
                    ? new Color(180, 50, 50)
                    : new Color(33, 87, 141));
        };
        updateSummary.run();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        toolbar.add(lblSummary);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Pay Now button — enabled only when a PENDING/OVERDUE row is selected
        JButton btnPay = new JButton("Pay Now");
        btnPay.setBackground(new Color(33, 120, 80));
        btnPay.setForeground(Color.WHITE);
        btnPay.setOpaque(true);
        btnPay.setBorderPainted(false);
        btnPay.setEnabled(false);

        JButton btnRefresh = new JButton("Refresh");

        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { btnPay.setEnabled(false); return; }
            String status = (String) model.getValueAt(row, 4);
            btnPay.setEnabled("PENDING".equals(status) || "OVERDUE".equals(status));
        });

        btnPay.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            int instId = (int) model.getValueAt(row, 0);
            int instNo = (int) model.getValueAt(row, 1);
            String amountStr = (String) model.getValueAt(row, 3);
            // Parse "1234.56 TL" → 1234.56
            double amount = Double.parseDouble(amountStr.replace(" TL", ""));

            String purpose = "Installment #" + instNo + "/12";
            PaymentResult result = MockPaymentDialog.showAndProcess(this, amount, purpose);

            if (result == PaymentResult.CANCELLED) return;

            if (!result.isSuccess()) {
                JOptionPane.showMessageDialog(this,
                        MockPaymentDialog.describe(result)
                                + "\n\nThe installment was not paid.",
                        "Payment Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                memberService.payInstallment(instId);
                JOptionPane.showMessageDialog(this,
                        "Installment #" + instNo + " paid successfully.",
                        "Payment Successful", JOptionPane.INFORMATION_MESSAGE);
                load.run();
                updateSummary.run();
                btnPay.setEnabled(false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Payment processed but database update failed:\n" + ex.getMessage()
                                + "\n\nPlease contact the club.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnRefresh.addActionListener(e -> {
            load.run();
            updateSummary.run();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        actions.add(btnRefresh);
        actions.add(btnPay);
        panel.add(actions, BorderLayout.SOUTH);

        return panel;
    }

    private JTextField readOnly(String value) {
        JTextField field = new JTextField(value);
        field.setEditable(false);
        field.setBackground(new Color(240, 240, 240));
        return field;
    }
}