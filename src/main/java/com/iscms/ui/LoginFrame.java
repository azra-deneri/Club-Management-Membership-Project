package com.iscms.ui;

import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.model.Trainer;
import com.iscms.service.AuthService;
import com.iscms.service.LoginResult;

import javax.swing.*;
import java.awt.*;

// Login screen — entry point for all three roles: Member, Manager, Trainer
public class LoginFrame extends JFrame {

    private final AuthService authService = new AuthService();
    // Used only for hint-rendering on the login screen (overdue count for the
// PAYMENT_HOLD notice). Keeping it as a field avoids re-instantiating on every
// login click. AuthService is the source of truth for actual auth decisions.
    private final com.iscms.service.MemberService memberServiceForUiHints =
            new com.iscms.service.MemberService();

    private JTextField txtIdentifier;
    private JPasswordField txtPassword;
    private JRadioButton rbMember, rbManager, rbTrainer;
    private JLabel lblIdentifier;

    public LoginFrame() {
        setTitle("ISC-MS — Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(420, 320);
        setLocationRelativeTo(null);
        setResizable(false);
        initUI();
    }

    private void initUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblTitle = new JLabel("Istanbul Sports Club", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 16));
        lblTitle.setForeground(new Color(33, 87, 141));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        panel.add(lblTitle, c);

        rbMember  = new JRadioButton("Member",  true);
        rbManager = new JRadioButton("Manager");
        rbTrainer = new JRadioButton("Trainer");
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbMember); bg.add(rbManager); bg.add(rbTrainer);

        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        rolePanel.add(rbMember); rolePanel.add(rbManager); rolePanel.add(rbTrainer);
        c.gridy = 1; panel.add(rolePanel, c);

        lblIdentifier = new JLabel("Phone Number:");
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weightx = 0.35;
        panel.add(lblIdentifier, c);

        txtIdentifier = new JTextField(20);
        c.gridx = 1; c.weightx = 0.65;
        panel.add(txtIdentifier, c);

        c.gridx = 0; c.gridy = 3; c.weightx = 0.35;
        panel.add(new JLabel("Password:"), c);
        txtPassword = new JPasswordField(20);
        c.gridx = 1; c.weightx = 0.65;
        panel.add(txtPassword, c);

        JButton btnLogin    = new JButton("Login");
        JButton btnRegister = new JButton("Register");
        JButton btnForgot   = new JButton("Forgot Password?");

        btnLogin.setBackground(new Color(33, 87, 141));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setOpaque(true);
        btnLogin.setBorderPainted(false);

        btnRegister.setBackground(new Color(33, 120, 80));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setOpaque(true);
        btnRegister.setBorderPainted(false);

        btnForgot.setBorderPainted(false);
        btnForgot.setContentAreaFilled(false);
        btnForgot.setForeground(Color.BLUE);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.add(btnLogin); btnPanel.add(btnRegister);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        panel.add(btnPanel, c);

        c.gridy = 5; panel.add(btnForgot, c);
        add(panel);

        rbMember.addActionListener(e  -> lblIdentifier.setText("Phone Number:"));
        rbManager.addActionListener(e -> lblIdentifier.setText("Email:"));
        rbTrainer.addActionListener(e -> lblIdentifier.setText("Username:"));

        btnLogin.addActionListener(e    -> handleLogin());
        btnRegister.addActionListener(e -> { dispose(); new RegisterFrame().setVisible(true); });
        btnForgot.addActionListener(e   -> new ForgotPasswordFrame().setVisible(true));
    }

    private void handleLogin() {
        String identifier = txtIdentifier.getText().trim();
        String password   = new String(txtPassword.getPassword());

        if (identifier.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LoginResult result;
        if (rbMember.isSelected())       result = authService.loginMember(identifier, password);
        else if (rbManager.isSelected()) result = authService.loginManager(identifier, password);
        else                             result = authService.loginTrainer(identifier, password);

        switch (result.getStatus()) {

            case SUCCESS -> {
                if (rbMember.isSelected()) {
                    Member loggedInMember = (Member) result.getUser();

                    // Payment hold notice — member crossed the overdue threshold and was
                    // auto-suspended pending payment. We let them in (they need to be able
                    // to pay) but the dashboard will render in restricted mode.
                    if ("PAYMENT_HOLD".equals(loggedInMember.getStatus())) {
                        long overdue = memberServiceForUiHints
                                .getInstallmentsForMember(loggedInMember.getMemberId())
                                .stream()
                                .filter(i -> "OVERDUE".equals(i.getStatus()))
                                .count();
                        JOptionPane.showMessageDialog(this,
                                "Account on Payment Hold\n\n"
                                        + "You have " + overdue + " overdue installment payments.\n"
                                        + "Your account access is limited until the overdue\n"
                                        + "balance is cleared.\n\n"
                                        + "Please go to the Installments tab and pay your\n"
                                        + "outstanding installments to restore full access.",
                                "Payment Hold", JOptionPane.WARNING_MESSAGE);
                    }

                    dispose();
                    new MemberDashboard(loggedInMember).setVisible(true);
                } else if (rbTrainer.isSelected()) {
                    dispose();
                    new TrainerDashboard((Trainer) result.getUser()).setVisible(true);
                } else {
                    dispose();
                    Manager mgr = (Manager) result.getUser();
                    if ("ADMIN".equals(mgr.getRole()))
                        new AdminDashboard(mgr).setVisible(true);
                    else
                        new ManagerDashboard(mgr).setVisible(true);
                }
            }

            case NOT_FOUND ->
                    JOptionPane.showMessageDialog(this,
                            "Account not found.", "Error", JOptionPane.ERROR_MESSAGE);

            // Wrong password — show remaining attempts with role-specific contact info
            // remainingTries == -1 signals "unlimited" (admin accounts) — no count shown
            case WRONG_PASSWORD -> {
                int rem = result.getRemainingTries();
                String msg;
                if (rem == -1) {
                    // Admin account — no lockout, no attempt count
                    msg = "Wrong password.";
                } else if (rbManager.isSelected()) {
                    msg = "Wrong password. " + rem + " attempt(s) remaining.\n"
                            + "If locked, contact the system administrator.";
                } else if (rbTrainer.isSelected()) {
                    msg = "Wrong password. " + rem + " attempt(s) remaining.\n"
                            + "If locked, contact the club manager.";
                } else {
                    msg = "Wrong password. " + rem + " attempt(s) remaining.";
                }
                JOptionPane.showMessageDialog(this, msg,
                        "Wrong Password", JOptionPane.ERROR_MESSAGE);
            }

            case SUGGEST_RESET -> {
                int choice = JOptionPane.showConfirmDialog(this,
                        "You have entered the wrong password 3 times.\n"
                                + "Would you like to reset your password?\n\n"
                                + "If you skip, you have 3 more attempts before your account is locked.",
                        "Reset Password?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice == JOptionPane.YES_OPTION)
                    new ForgotPasswordFrame().setVisible(true);
            }

            case LOCKED ->
                    JOptionPane.showMessageDialog(this,
                            "Your account has been locked.\n"
                                    + "Please contact the club to unlock your account.",
                            "Account Locked", JOptionPane.ERROR_MESSAGE);

            case SUSPENDED ->
                    JOptionPane.showMessageDialog(this,
                            "Your account has been suspended.\n"
                                    + "Please contact the club.",
                            "Suspended", JOptionPane.ERROR_MESSAGE);

            case PENDING ->
                    JOptionPane.showMessageDialog(this,
                            "Registration Pending\n\n"
                                    + "Your registration is awaiting manager approval.\n"
                                    + "Please ensure payment has been made.\n"
                                    + "Approval may take up to 3 days.",
                            "Pending Approval", JOptionPane.INFORMATION_MESSAGE);

            case REGISTRATION_FAILED ->
                    JOptionPane.showMessageDialog(this,
                            "Registration Expired\n\n"
                                    + "Your registration request has expired.\n"
                                    + "Payment was not received within 3 days.\n"
                                    + "Please register again.",
                            "Registration Expired", JOptionPane.ERROR_MESSAGE);

            case FROZEN ->
                    JOptionPane.showMessageDialog(this,
                            "Membership Frozen\n\n"
                                    + "Your membership is currently frozen.\n"
                                    + "You can log in again after the freeze period ends.",
                            "Account Frozen", JOptionPane.WARNING_MESSAGE);

            case PASSIVE -> {
                JOptionPane.showMessageDialog(this,
                        "Your membership has expired.\n"
                                + "You can log in and submit a renewal request.",
                        "Membership Expired", JOptionPane.WARNING_MESSAGE);
                dispose();
                new MemberDashboard((Member) result.getUser()).setVisible(true);
            }
        }
    }
}