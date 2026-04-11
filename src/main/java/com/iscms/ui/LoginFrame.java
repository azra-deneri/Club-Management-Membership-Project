package com.iscms.ui;

import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.model.Trainer;
import com.iscms.service.AuthService;
import com.iscms.service.LoginResult;

import javax.swing.*;
import java.awt.*;

// Login screen — entry point for all three roles: Member, Manager, Trainer
// Role selection changes the identifier label (phone / email / username)
public class LoginFrame extends JFrame {

    // AuthService handles all login logic and password operations
    private final AuthService authService = new AuthService();

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

    // Builds the login form with role selection, identifier, password, and action buttons
    private void initUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Title label
        JLabel lblTitle = new JLabel("Istanbul Sports Club", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 16));
        lblTitle.setForeground(new Color(33, 87, 141));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        panel.add(lblTitle, c);

        // Role selection radio buttons — Member is selected by default
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
        c.gridx = 0; c.gridy = 2; c.gridwidth = 1; c.weightx = 0.35;
        panel.add(lblIdentifier, c);

        txtIdentifier = new JTextField(20);
        c.gridx = 1; c.weightx = 0.65;
        panel.add(txtIdentifier, c);

        // Password field
        c.gridx = 0; c.gridy = 3; c.weightx = 0.35;
        panel.add(new JLabel("Password:"), c);
        txtPassword = new JPasswordField(20);
        c.gridx = 1; c.weightx = 0.65;
        panel.add(txtPassword, c);

        // Action buttons
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

        // Role radio buttons update the identifier label dynamically
        rbMember.addActionListener(e  -> lblIdentifier.setText("Phone Number:"));
        rbManager.addActionListener(e -> lblIdentifier.setText("Email:"));
        rbTrainer.addActionListener(e -> lblIdentifier.setText("Username:"));

        // Button listeners
        btnLogin.addActionListener(e    -> handleLogin());
        btnRegister.addActionListener(e -> { dispose(); new RegisterFrame().setVisible(true); });
        btnForgot.addActionListener(e   -> new ForgotPasswordFrame().setVisible(true));
    }

    // Handles the login button action
    // Delegates authentication to AuthService based on selected role
    // Routes to the correct dashboard based on role and result
    private void handleLogin() {
        String identifier = txtIdentifier.getText().trim();
        String password   = new String(txtPassword.getPassword());

        // Basic validation — both fields must be filled
        if (identifier.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Call the appropriate login method based on selected role
        LoginResult result;
        if (rbMember.isSelected())       result = authService.loginMember(identifier, password);
        else if (rbManager.isSelected()) result = authService.loginManager(identifier, password);
        else                             result = authService.loginTrainer(identifier, password);

        // Handle all possible login outcomes
        switch (result.getStatus()) {

            // Successful login — route to correct dashboard based on role
            case SUCCESS -> {
                dispose();
                if (rbMember.isSelected()) {
                    new MemberDashboard((Member) result.getUser()).setVisible(true);
                } else if (rbTrainer.isSelected()) {
                    new TrainerDashboard((Trainer) result.getUser()).setVisible(true);
                } else {
                    // Manager role — check if ADMIN or MANAGER to open correct dashboard
                    Manager mgr = (Manager) result.getUser();
                    if ("ADMIN".equals(mgr.getRole()))
                        new AdminDashboard(mgr).setVisible(true);
                    else
                        new ManagerDashboard(mgr).setVisible(true);
                }
            }

            // No account found with the given credentials
            case NOT_FOUND ->
                    JOptionPane.showMessageDialog(this,
                            "Account not found.", "Error", JOptionPane.ERROR_MESSAGE);

            // Wrong password — show remaining attempts with role-specific contact info
            case WRONG_PASSWORD -> {
                int rem = result.getRemainingTries();
                String msg;
                if (rbManager.isSelected())
                    msg = "Wrong password. " + rem + " attempt(s) remaining.\n"
                            + "If locked, contact the system administrator.";
                else if (rbTrainer.isSelected())
                    msg = "Wrong password. " + rem + " attempt(s) remaining.\n"
                            + "If locked, contact the club manager.";
                else
                    msg = "Wrong password. " + rem + " attempt(s) remaining.";
                JOptionPane.showMessageDialog(this, msg,
                        "Wrong Password", JOptionPane.ERROR_MESSAGE);
            }

            // Reached the suggest-reset threshold — offer to open ForgotPasswordFrame
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

            // Account locked — too many failed attempts
            case LOCKED ->
                    JOptionPane.showMessageDialog(this,
                            "Your account has been locked.\n"
                                    + "Please contact the club to unlock your account.",
                            "Account Locked", JOptionPane.ERROR_MESSAGE);

            // Member suspended by manager / Trainer deactivated
            case SUSPENDED ->
                    JOptionPane.showMessageDialog(this,
                            "Your account has been suspended.\n"
                                    + "Please contact the club.",
                            "Suspended", JOptionPane.ERROR_MESSAGE);

            // Member registration awaiting manager approval
            case PENDING ->
                    JOptionPane.showMessageDialog(this,
                            "Registration Pending\n\n"
                                    + "Your registration is awaiting manager approval.\n"
                                    + "Please ensure payment has been made.\n"
                                    + "Approval may take up to 3 days.",
                            "Pending Approval", JOptionPane.INFORMATION_MESSAGE);

            // Registration request expired — member must register again
            case REGISTRATION_FAILED ->
                    JOptionPane.showMessageDialog(this,
                            "Registration Expired\n\n"
                                    + "Your registration request has expired.\n"
                                    + "Payment was not received within 3 days.\n"
                                    + "Please register again.",
                            "Registration Expired", JOptionPane.ERROR_MESSAGE);

            // Membership is frozen — member cannot log in until freeze period ends
            case FROZEN ->
                    JOptionPane.showMessageDialog(this,
                            "Membership Frozen\n\n"
                                    + "Your membership is currently frozen.\n"
                                    + "You can log in again after the freeze period ends.",
                            "Account Frozen", JOptionPane.WARNING_MESSAGE);

            // Membership expired — member can still log in to submit a renewal request
            case PASSIVE -> {
                JOptionPane.showMessageDialog(this,
                        "Your membership has expired.\n"
                                + "You can log in and submit a renewal request.",
                        "Membership Expired", JOptionPane.WARNING_MESSAGE);
                dispose();
                // Open dashboard so member can navigate to renewal form
                new MemberDashboard((Member) result.getUser()).setVisible(true);
            }
        }
    }
}