package com.iscms.ui;

import com.iscms.model.Manager;
import com.iscms.model.Payment;
import com.iscms.service.ManagerService;
import com.iscms.service.ReportService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

// Admin dashboard — only accessible by users with ADMIN role
// Provides 3 tabs: Manager list, Add Manager form, All Payments
public class AdminDashboard extends JFrame {

    // The currently logged-in admin
    private final Manager admin;

    // Service instances — no DAOs directly in UI layer
    private final ManagerService managerService = new ManagerService();
    private final ReportService reportService   = new ReportService();

    // Table and model for the manager list tab
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

    // Builds the main layout: top bar + tabbed pane
    private void initUI() {
        // Top bar with title and logout button
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(60, 30, 100));
        topBar.setPreferredSize(new Dimension(0, 50));

        JLabel lblTitle = new JLabel("  ISC-MS | ADMIN: " + admin.getFullName());
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

        // Three tabs: manager list, add manager form, all payments
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Managers",     buildManagersPanel());
        tabs.addTab("Add Manager",  buildAddManagerPanel());
        tabs.addTab("All Payments", buildPaymentsPanel());
        add(tabs, BorderLayout.CENTER);
    }

    // Handles password reset for the selected manager
    // Validates: not empty, minimum 8 characters, passwords match
    private void resetManagerPassword() {
        int row = managerTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a manager.");
            return;
        }
        int managerId = (int) managerModel.getValueAt(row, 0);
        String name   = (String) managerModel.getValueAt(row, 1);

        JPasswordField txtNew     = new JPasswordField(20);
        JPasswordField txtConfirm = new JPasswordField(20);

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.add(new JLabel("New Password:"));     panel.add(txtNew);
        panel.add(new JLabel("Confirm Password:")); panel.add(txtConfirm);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Reset Password — " + name, JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) return;

        String newPass = new String(txtNew.getPassword());
        String confirm = new String(txtConfirm.getPassword());

        if (newPass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Password cannot be empty.");
            return;
        }
        if (newPass.length() < 8) {
            JOptionPane.showMessageDialog(this,
                    "Password must be at least 8 characters.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!newPass.equals(confirm)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.");
            return;
        }

        // Delegate to service layer — hashing happens in ManagerService
        managerService.resetManagerPassword(managerId, newPass);
        JOptionPane.showMessageDialog(this, "Password reset successfully.");
    }

    // Builds the Managers tab — table with lock/unlock, delete, reset password actions
    private JPanel buildManagersPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Action buttons toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh   = new JButton("Refresh");
        JButton btnLock      = new JButton("Lock / Unlock");
        JButton btnDelete    = new JButton("Delete Manager");
        JButton btnResetPass = new JButton("Reset Password");

        btnLock.setBackground(new Color(200, 130, 0));
        btnLock.setForeground(Color.WHITE);
        btnLock.setOpaque(true);
        btnLock.setBorderPainted(false);

        btnDelete.setBackground(new Color(150, 50, 50));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.setOpaque(true);
        btnDelete.setBorderPainted(false);

        btnResetPass.setBackground(new Color(33, 87, 141));
        btnResetPass.setForeground(Color.WHITE);
        btnResetPass.setOpaque(true);
        btnResetPass.setBorderPainted(false);

        toolbar.add(btnRefresh);
        toolbar.add(btnLock);
        toolbar.add(btnDelete);
        toolbar.add(btnResetPass);
        panel.add(toolbar, BorderLayout.NORTH);

        // Non-editable table for displaying manager records
        String[] cols = {"ID", "Full Name", "Username", "Email", "Role", "Locked", "Created"};
        managerModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        managerTable = new JTable(managerModel);
        managerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        managerTable.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(managerTable), BorderLayout.CENTER);

        btnRefresh.addActionListener(e   -> loadManagers());
        btnLock.addActionListener(e      -> toggleLock());
        btnDelete.addActionListener(e    -> deleteManager());
        btnResetPass.addActionListener(e -> resetManagerPassword());

        return panel;
    }

    // Loads all managers from the database and populates the table
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

    // Toggles the lock status of the selected manager
    // Admin cannot lock their own account
    private void toggleLock() {
        int row = managerTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Please select a manager."); return; }

        int managerId = (int) managerModel.getValueAt(row, 0);

        // Prevent admin from locking their own account
        if (managerId == admin.getManagerId()) {
            JOptionPane.showMessageDialog(this, "You cannot lock yourself.");
            return;
        }

        // Toggle: if currently Active → lock, if currently LOCKED → unlock
        String lockStatus = (String) managerModel.getValueAt(row, 5);
        boolean newStatus = "Active".equals(lockStatus);
        managerService.setLockStatus(managerId, newStatus);
        loadManagers();
        JOptionPane.showMessageDialog(this,
                "Manager " + (newStatus ? "locked." : "unlocked."));
    }

    // Deletes the selected manager after confirmation
    // Admin cannot delete their own account
    private void deleteManager() {
        int row = managerTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Please select a manager."); return; }

        int managerId = (int) managerModel.getValueAt(row, 0);

        // Prevent admin from deleting their own account
        if (managerId == admin.getManagerId()) {
            JOptionPane.showMessageDialog(this, "You cannot delete yourself.");
            return;
        }

        String name = (String) managerModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete manager '" + name + "'?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            // Service handles FK cleanup (nullifyManagerEvents) before deletion
            managerService.removeManager(managerId);
            loadManagers();
        }
    }

    // Builds the Add Manager tab — form for creating a new manager account
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

        String[] labels    = {"Full Name *", "Username *", "Email *", "Password *", "Role *"};
        Component[] inputs = {txtName, txtUser, txtEmail, txtPass, cbRole};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.3;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.7;
            panel.add(inputs[i], c);
        }

        JButton btnSave = new JButton("Add Manager");
        btnSave.setBackground(new Color(60, 30, 100));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        btnSave.addActionListener(e -> {
            String name  = txtName.getText().trim();
            String user  = txtUser.getText().trim();
            String email = txtEmail.getText().trim();
            String pass  = new String(txtPass.getPassword());
            String role  = (String) cbRole.getSelectedItem();

            // Validate all required fields are filled
            if (name.isEmpty() || user.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please fill in all fields.");
                return;
            }
            try {
                Manager m = new Manager();
                m.setFullName(name);
                m.setUsername(user);
                m.setEmail(email);
                m.setPassword(pass);
                m.setRole(role);
                // Service layer handles password hashing before insert
                managerService.addManager(m);
                JOptionPane.showMessageDialog(panel, "Manager added successfully!");
                loadManagers();
                // Clear form fields after successful add
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
    private JPanel buildPaymentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"ID", "Member ID", "Amount", "Date", "Type", "Description", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        // Load all payments from service and populate table
        for (Payment p : reportService.getAllPayments()) {
            model.addRow(new Object[]{
                    p.getPaymentId(), p.getMemberId(),
                    String.format("%.2f TL", p.getAmount()),
                    p.getPaymentDate().toLocalDate(),
                    p.getPaymentType(), p.getDescription(), p.getStatus()
            });
        }

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }
}