package com.iscms.ui;

import com.iscms.model.Manager;
import com.iscms.model.Payment;
import com.iscms.service.AuthService;
import com.iscms.service.ManagerService;
import com.iscms.service.ReportService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

// Admin dashboard — only accessible by users with ADMIN role
// Provides 4 tabs: Manager list, Add Manager, All Payments, My Profile
// Note: Admin can no longer reset a manager's password — managers must use
// the Forgot Password flow or update their password from their own profile.
// Admin updates their own password from the My Profile tab here.
public class AdminDashboard extends JFrame {

    private final Manager admin;
    private final ManagerService managerService = new ManagerService();
    private final ReportService reportService   = new ReportService();
    private final AuthService   authService     = new AuthService();

    private JTable managerTable;
    private DefaultTableModel managerModel;

    public AdminDashboard(Manager admin) {
        this.admin = admin;
        setTitle("Admin Panel — " + admin.getFullName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        initUI();
        loadManagers();
    }

    private void initUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(60, 30, 100));
        topBar.setPreferredSize(new Dimension(0, 50));

        JLabel lblTitle = new JLabel("  ISC-MS | ADMIN: " + admin.getFullName());
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

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Managers",     buildManagersPanel());
        tabs.addTab("Add Manager",  buildAddManagerPanel());
        tabs.addTab("All Payments", buildPaymentsPanel());
        tabs.addTab("My Profile",   buildMyProfilePanel());
        add(tabs, BorderLayout.CENTER);
    }

    // Builds the Managers tab — table with lock/unlock, delete actions
    // Reset Password button removed: managers manage their own passwords
    private JPanel buildManagersPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh = new JButton("Refresh");
        JButton btnLock    = new JButton("Lock / Unlock");
        JButton btnDelete  = new JButton("Delete Manager");

        btnLock.setBackground(new Color(200, 130, 0));
        btnLock.setForeground(Color.WHITE);
        btnLock.setOpaque(true);
        btnLock.setBorderPainted(false);

        btnDelete.setBackground(new Color(150, 50, 50));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.setOpaque(true);
        btnDelete.setBorderPainted(false);

        toolbar.add(btnRefresh);
        toolbar.add(btnLock);
        toolbar.add(btnDelete);
        panel.add(toolbar, BorderLayout.NORTH);

        // ID kept in model column 0 so action handlers can read it via getValueAt(row, 0)
        String[] cols = {"ID", "Full Name", "Username", "Email", "Role", "Locked", "Created"};
        managerModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        managerTable = new JTable(managerModel);
        managerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        managerTable.getTableHeader().setReorderingAllowed(false);
        managerTable.removeColumn(managerTable.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(managerTable), BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> loadManagers());
        btnLock.addActionListener(e    -> toggleLock());
        btnDelete.addActionListener(e  -> deleteManager());

        return panel;
    }

    private void loadManagers() {
        managerModel.setRowCount(0);
        for (Manager m : managerService.getAllManagers()) {
            managerModel.addRow(new Object[]{
                    m.getManagerId(), m.getFullName(), m.getUsername(),
                    m.getEmail(), m.getRole(),
                    m.isLocked() ? "LOCKED" : "Active",
                    m.getCreatedAt().toLocalDate()
            });
        }
    }

    private void toggleLock() {
        int row = managerTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Please select a manager."); return; }

        int managerId = (int) managerModel.getValueAt(row, 0);

        if (managerId == admin.getManagerId()) {
            JOptionPane.showMessageDialog(this, "You cannot lock yourself.");
            return;
        }

        String lockStatus = (String) managerModel.getValueAt(row, 5);
        boolean newStatus = "Active".equals(lockStatus);
        managerService.setLockStatus(managerId, newStatus);
        loadManagers();
        JOptionPane.showMessageDialog(this,
                "Manager " + (newStatus ? "locked." : "unlocked."));
    }

    private void deleteManager() {
        int row = managerTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Please select a manager."); return; }

        int managerId = (int) managerModel.getValueAt(row, 0);

        if (managerId == admin.getManagerId()) {
            JOptionPane.showMessageDialog(this, "You cannot delete yourself.");
            return;
        }

        String name = (String) managerModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete manager '" + name + "'?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            managerService.removeManager(managerId);
            loadManagers();
        }
    }

    // Builds the Add Manager tab — form for creating a new manager account
    // Admin sets the initial password here. After login, the manager can change
    // their own password from their My Profile tab in ManagerDashboard.
    private JPanel buildAddManagerPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtName  = new JTextField(20);
        JTextField txtUser  = new JTextField(20);
        JTextField txtEmail = new JTextField(20);
        JPasswordField txtPass = new JPasswordField(20);
        JComboBox<String> cbRole = new JComboBox<>(new String[]{"MANAGER", "ADMIN"});

        String[] labels    = {"Full Name *", "Username *", "Email *", "Initial Password *", "Role *"};
        Component[] inputs = {txtName, txtUser, txtEmail, txtPass, cbRole};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.3;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.7;
            panel.add(inputs[i], c);
        }

        JLabel lblHint = new JLabel(
                "<html><i>Note: The manager can change this password from their profile after first login.</i></html>");
        lblHint.setForeground(new Color(100, 100, 100));
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(lblHint, c);

        JButton btnSave = new JButton("Add Manager");
        btnSave.setBackground(new Color(60, 30, 100));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridy = inputs.length + 1;
        panel.add(btnSave, c);

        btnSave.addActionListener(e -> {
            String name  = txtName.getText().trim();
            String user  = txtUser.getText().trim();
            String email = txtEmail.getText().trim();
            String pass  = new String(txtPass.getPassword());
            String role  = (String) cbRole.getSelectedItem();

            if (name.isEmpty() || user.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please fill in all fields.");
                return;
            }
            if (pass.length() < 8) {
                JOptionPane.showMessageDialog(panel,
                        "Initial password must be at least 8 characters.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                Manager m = new Manager();
                m.setFullName(name);
                m.setUsername(user);
                m.setEmail(email);
                m.setPassword(pass);
                m.setRole(role);
                managerService.addManager(m);
                JOptionPane.showMessageDialog(panel, "Manager added successfully!");
                loadManagers();
                txtName.setText(""); txtUser.setText("");
                txtEmail.setText(""); txtPass.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    // Builds the All Payments tab — read-only table showing all payment records
    // Payment ID hidden in view; Member ID column dropped entirely
    private JPanel buildPaymentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"PaymentID", "Amount", "Date", "Type", "Description", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        for (Payment p : reportService.getAllPayments()) {
            model.addRow(new Object[]{
                    p.getPaymentId(),
                    String.format("%.2f TL", p.getAmount()),
                    p.getPaymentDate().toLocalDate(),
                    p.getPaymentType(), p.getDescription(), p.getStatus()
            });
        }

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // --- My Profile Tab ---
    // Admin changes their own password from here.
    // Same flow as ManagerDashboard's My Profile — current password verified first.
    // Goes through resetManagerPasswordByEmail since admin is stored in the manager table.
    private JPanel buildMyProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtName  = readOnly(admin.getFullName());
        JTextField txtUser  = readOnly(admin.getUsername() != null ? admin.getUsername() : "");
        JTextField txtEmail = readOnly(admin.getEmail() != null ? admin.getEmail() : "");
        JTextField txtRole  = readOnly(admin.getRole());

        JPasswordField txtCurrent = new JPasswordField(20);
        JPasswordField txtNew     = new JPasswordField(20);
        JPasswordField txtConfirm = new JPasswordField(20);

        String[] labels    = {"Full Name (read-only)", "Username (read-only)",
                "Email (read-only)", "Role (read-only)",
                "Current Password", "New Password", "Confirm New Password"};
        Component[] inputs = {txtName, txtUser, txtEmail, txtRole,
                txtCurrent, txtNew, txtConfirm};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.35;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.65;
            panel.add(inputs[i], c);
        }

        JButton btnSave = new JButton("Update Password");
        btnSave.setBackground(new Color(60, 30, 100));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        btnSave.addActionListener(e -> {
            String current = new String(txtCurrent.getPassword());
            String pass    = new String(txtNew.getPassword());
            String confirm = new String(txtConfirm.getPassword());

            if (current.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please fill in all password fields.");
                return;
            }
            if (pass.length() < 8) {
                JOptionPane.showMessageDialog(panel,
                        "New password must be at least 8 characters.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!pass.equals(confirm)) {
                JOptionPane.showMessageDialog(panel, "New passwords do not match.");
                return;
            }
            if (pass.equals(current)) {
                JOptionPane.showMessageDialog(panel,
                        "New password cannot be the same as your current password.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Verify current password before applying the change
            if (!authService.verifyManagerPassword(admin.getManagerId(), current)) {
                JOptionPane.showMessageDialog(panel,
                        "Current password is incorrect.",
                        "Authentication Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Apply via service — handles BCrypt hashing + lock clearing
            AuthService.ResetResult result =
                    authService.resetManagerPasswordByEmail(admin.getEmail(), pass);
            if (result == AuthService.ResetResult.SUCCESS) {
                JOptionPane.showMessageDialog(panel, "Password updated successfully.");
                txtCurrent.setText("");
                txtNew.setText("");
                txtConfirm.setText("");
            } else {
                JOptionPane.showMessageDialog(panel,
                        "Could not update password. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

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