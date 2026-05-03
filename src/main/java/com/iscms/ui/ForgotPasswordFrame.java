package com.iscms.ui;

import com.iscms.service.AuthService;

import javax.swing.*;
import java.awt.*;

// Screen for resetting a password using an identifier (no current password needed)
// Accessible from the login screen via "Forgot Password" link
// Supports all three roles: Member (phone), Manager (email), Trainer (username)
// Note: Admin accounts are part of the Manager role — reset via the Manager option
public class ForgotPasswordFrame extends JFrame {

    private final AuthService authService = new AuthService();

    private JRadioButton rbMember, rbManager, rbTrainer;
    private JLabel lblIdentifier;
    private JTextField txtIdentifier;
    private JPasswordField txtNew;
    private JPasswordField txtConfirm;

    public ForgotPasswordFrame() {
        setTitle("Reset Password");
        setSize(440, 320);
        setLocationRelativeTo(null);
        setResizable(false);
        initUI();
    }

    private void initUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Title
        JLabel lblTitle = new JLabel("Reset Password", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 16));
        lblTitle.setForeground(new Color(33, 87, 141));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        panel.add(lblTitle, c);

        // Role selection — same layout as LoginFrame for consistency
        rbMember  = new JRadioButton("Member",  true);
        rbManager = new JRadioButton("Manager");
        rbTrainer = new JRadioButton("Trainer");
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbMember); bg.add(rbManager); bg.add(rbTrainer);

        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        rolePanel.add(rbMember); rolePanel.add(rbManager); rolePanel.add(rbTrainer);
        c.gridy = 1; panel.add(rolePanel, c);

        // Identifier field — label changes based on selected role
        lblIdentifier = new JLabel("Phone Number:");
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weightx = 0.4;
        panel.add(lblIdentifier, c);

        txtIdentifier = new JTextField(20);
        c.gridx = 1; c.weightx = 0.6;
        panel.add(txtIdentifier, c);

        // New password
        c.gridx = 0; c.gridy = 3; c.weightx = 0.4;
        panel.add(new JLabel("New Password:"), c);
        txtNew = new JPasswordField(20);
        c.gridx = 1; c.weightx = 0.6;
        panel.add(txtNew, c);

        // Confirm password
        c.gridx = 0; c.gridy = 4; c.weightx = 0.4;
        panel.add(new JLabel("Confirm Password:"), c);
        txtConfirm = new JPasswordField(20);
        c.gridx = 1; c.weightx = 0.6;
        panel.add(txtConfirm, c);

        // Reset button
        JButton btnReset = new JButton("Reset Password");
        btnReset.setBackground(new Color(33, 87, 141));
        btnReset.setForeground(Color.WHITE);
        btnReset.setOpaque(true);
        btnReset.setBorderPainted(false);
        c.gridx = 0; c.gridy = 5; c.gridwidth = 2;
        panel.add(btnReset, c);

        add(panel);

        // Update identifier label dynamically when role changes
        rbMember.addActionListener(e  -> lblIdentifier.setText("Phone Number:"));
        rbManager.addActionListener(e -> lblIdentifier.setText("Email:"));
        rbTrainer.addActionListener(e -> lblIdentifier.setText("Username:"));

        btnReset.addActionListener(e -> handleReset());
    }

    // Validates input and dispatches to the correct AuthService reset method based on role
    private void handleReset() {
        String identifier = txtIdentifier.getText().trim();
        String newPass    = new String(txtNew.getPassword());
        String confirm    = new String(txtConfirm.getPassword());

        // Validation: required fields
        if (identifier.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.");
            return;
        }

        // Validation: minimum password length
        if (newPass.length() < 8) {
            JOptionPane.showMessageDialog(this,
                    "Password must be at least 8 characters.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validation: passwords must match
        if (!newPass.equals(confirm)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.");
            return;
        }

        // Dispatch to the correct reset method based on selected role
        AuthService.ResetResult result;
        String roleLabel;
        if (rbMember.isSelected()) {
            result = authService.resetMemberPassword(identifier, newPass);
            roleLabel = "Member";
        } else if (rbManager.isSelected()) {
            // Covers both MANAGER and ADMIN roles — admin uses the same reset path
            result = authService.resetManagerPasswordByEmail(identifier, newPass);
            roleLabel = "Manager";
        } else {
            result = authService.resetTrainerPasswordByUsername(identifier, newPass);
            roleLabel = "Trainer";
        }

        switch (result) {
            case SUCCESS -> {
                JOptionPane.showMessageDialog(this,
                        "Password reset successful! You can now log in.");
                dispose();
            }
            case SAME_AS_OLD -> JOptionPane.showMessageDialog(this,
                    "New password cannot be the same as your current password.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            case NOT_FOUND -> JOptionPane.showMessageDialog(this,
                    roleLabel + " account not found.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}