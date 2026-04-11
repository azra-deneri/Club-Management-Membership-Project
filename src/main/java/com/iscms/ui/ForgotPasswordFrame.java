package com.iscms.ui;

import com.iscms.service.AuthService;

import javax.swing.*;
import java.awt.*;

// Screen for resetting a member's password using their phone number
// Accessible from the login screen via "Forgot Password" link
public class ForgotPasswordFrame extends JFrame {

    // AuthService handles all password reset business logic
    private final AuthService authService = new AuthService();

    public ForgotPasswordFrame() {
        setTitle("Reset Password");
        setSize(420, 260);
        setLocationRelativeTo(null);
        setResizable(false);
        initUI();
    }

    // Builds the password reset form
    private void initUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Input fields
        JTextField txtPhone       = new JTextField();
        JPasswordField txtNew     = new JPasswordField();
        JPasswordField txtConfirm = new JPasswordField();

        // Phone number row
        c.gridx = 0; c.gridy = 0; c.weightx = 0.4;
        panel.add(new JLabel("Phone Number:"), c);
        c.gridx = 1; c.weightx = 0.6;
        panel.add(txtPhone, c);

        // New password row
        c.gridx = 0; c.gridy = 1; c.weightx = 0.4;
        panel.add(new JLabel("New Password:"), c);
        c.gridx = 1; c.weightx = 0.6;
        panel.add(txtNew, c);

        // Confirm password row
        c.gridx = 0; c.gridy = 2; c.weightx = 0.4;
        panel.add(new JLabel("Confirm Password:"), c);
        c.gridx = 1; c.weightx = 0.6;
        panel.add(txtConfirm, c);

        // Reset button
        JButton btnReset = new JButton("Reset Password");
        btnReset.setBackground(new Color(33, 87, 141));
        btnReset.setForeground(Color.WHITE);
        btnReset.setOpaque(true);
        btnReset.setBorderPainted(false);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        panel.add(btnReset, c);
        add(panel);

        btnReset.addActionListener(e -> {
            String phone   = txtPhone.getText().trim();
            String newPass = new String(txtNew.getPassword());
            String confirm = new String(txtConfirm.getPassword());

            // Validation step 1: required fields must not be empty
            if (phone.isEmpty() || newPass.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill in all fields.");
                return;
            }

            // Validation step 2: password must be at least 8 characters
            if (newPass.length() < 8) {
                JOptionPane.showMessageDialog(this,
                        "Password must be at least 8 characters.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Validation step 3: both password fields must match
            if (!newPass.equals(confirm)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.");
                return;
            }

            // Delegate to AuthService — handles same-as-old and not-found checks
            // BCrypt comparison and DB update happen in the service layer
            AuthService.ResetResult result = authService.resetMemberPassword(phone, newPass);
            switch (result) {
                case SUCCESS -> {
                    JOptionPane.showMessageDialog(this,
                            "Password reset successful! You can now login.");
                    dispose();
                }
                case SAME_AS_OLD -> JOptionPane.showMessageDialog(this,
                        "New password cannot be the same as your current password.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                case NOT_FOUND -> JOptionPane.showMessageDialog(this,
                        "Phone number not found.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}