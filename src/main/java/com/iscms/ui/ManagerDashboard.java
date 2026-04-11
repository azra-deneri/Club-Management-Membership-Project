package com.iscms.ui;

import com.iscms.model.*;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalTime;
import java.util.*;
import java.util.List;

// Manager dashboard — accessible by users with MANAGER role
// Provides 6 tabs: Members, Add Member, Requests, Events, Trainers & PT, Reports
public class ManagerDashboard extends JFrame {

    // The currently logged-in manager
    private final Manager manager;

    // Service instance — no DAOs directly in UI layer
    private final MemberService memberService = new MemberService();

    // Table and model for the member list tab
    private JTable memberTable;
    private DefaultTableModel tableModel;
    private JTextField txtSearch;

    public ManagerDashboard(Manager manager) {
        this.manager = manager;
        setTitle("Manager Panel — " + manager.getFullName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 680);
        setLocationRelativeTo(null);
        initUI();
        loadMembers();
    }

    // Builds the main layout: top bar + tabbed pane with 6 tabs
    private void initUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(33, 87, 141));
        topBar.setPreferredSize(new Dimension(0, 50));

        JLabel lblTitle = new JLabel("  ISC-MS | Manager: " + manager.getFullName());
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

        // Six main tabs — Events tab reuses EventManagementPanel, Reports reuses ReportsPanel
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Members",       buildMembersPanel());
        tabs.addTab("Add Member",    buildAddMemberPanel());
        tabs.addTab("Requests",      buildRequestsPanel());
        tabs.addTab("Events",        new EventManagementPanel(manager));
        tabs.addTab("Trainers & PT", buildTrainersPTPanel());
        tabs.addTab("Reports",       new ReportsPanel());
        add(tabs, BorderLayout.CENTER);
    }

    // --- Members Tab ---

    // Builds the member list panel with search, suspend, and unlock actions
    private JPanel buildMembersPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtSearch = new JTextField(15);
        JButton btnSearch  = new JButton("Search");
        JButton btnRefresh = new JButton("Refresh");
        JButton btnSuspend = new JButton("Suspend");
        JButton btnUnlock  = new JButton("Unlock");

        btnSuspend.setBackground(new Color(150, 50, 50));
        btnSuspend.setForeground(Color.WHITE);
        btnSuspend.setOpaque(true);
        btnSuspend.setBorderPainted(false);

        toolbar.add(new JLabel("Search:"));
        toolbar.add(txtSearch);
        toolbar.add(btnSearch);
        toolbar.add(btnRefresh);
        toolbar.add(btnSuspend);
        toolbar.add(btnUnlock);
        panel.add(toolbar, BorderLayout.NORTH);

        // Non-editable member table
        String[] cols = {"ID", "Full Name", "Phone", "Email", "Status", "Tier", "Created"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        memberTable = new JTable(tableModel);
        memberTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        memberTable.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(memberTable), BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> loadMembers());
        btnSearch.addActionListener(e  -> searchMembers());
        btnSuspend.addActionListener(e -> suspendMember());
        btnUnlock.addActionListener(e  -> unlockMember());

        return panel;
    }

    // Loads all members and resolves their active membership tier for display
    private void loadMembers() {
        tableModel.setRowCount(0);
        for (Member m : memberService.getAllMembers()) {
            Optional<Membership> ms = memberService.getActiveMembership(m.getMemberId());
            tableModel.addRow(new Object[]{
                    m.getMemberId(), m.getFullName(), m.getPhone(),
                    m.getEmail() != null ? m.getEmail() : "",
                    m.getStatus(),
                    ms.map(Membership::getTier).orElse("-"),
                    m.getCreatedAt().toLocalDate()
            });
        }
    }

    // Filters the member table by name or phone number
    private void searchMembers() {
        String query = txtSearch.getText().trim().toLowerCase();
        tableModel.setRowCount(0);
        for (Member m : memberService.getAllMembers()) {
            if (m.getFullName().toLowerCase().contains(query) || m.getPhone().contains(query)) {
                Optional<Membership> ms = memberService.getActiveMembership(m.getMemberId());
                tableModel.addRow(new Object[]{
                        m.getMemberId(), m.getFullName(), m.getPhone(),
                        m.getEmail() != null ? m.getEmail() : "",
                        m.getStatus(),
                        ms.map(Membership::getTier).orElse("-"),
                        m.getCreatedAt().toLocalDate()
                });
            }
        }
    }

    // Suspends the selected member after confirmation
    private void suspendMember() {
        int row = memberTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Please select a member."); return; }
        String name = (String) tableModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Suspend member '" + name + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            memberService.suspendMember((int) tableModel.getValueAt(row, 0));
            loadMembers();
        }
    }

    // Unlocks the selected member account and resets failed attempt counter
    private void unlockMember() {
        int row = memberTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Please select a member."); return; }
        memberService.unlockMember((int) tableModel.getValueAt(row, 0));
        JOptionPane.showMessageDialog(this, "Member unlocked successfully.");
        loadMembers();
    }

    // --- Add Member Tab ---

    // Builds the direct member registration form — manager adds member without approval flow
    private JPanel buildAddMemberPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtName     = new JTextField(20);
        JTextField txtDob      = new JTextField("YYYY-MM-DD");
        JComboBox<String> cbGender  = new JComboBox<>(new String[]{"MALE", "FEMALE", "OTHER"});
        JTextField txtPhone    = new JTextField(20);
        JTextField txtEmail    = new JTextField(20);
        JPasswordField txtPass = new JPasswordField(20);
        JComboBox<String> cbTier    = new JComboBox<>(new String[]{"CLASSIC", "GOLD", "VIP"});
        JComboBox<String> cbPackage = new JComboBox<>(
                new String[]{"MONTHLY", "ANNUAL_INSTALLMENT", "ANNUAL_PREPAID"});

        String[] labels    = {"Full Name *", "Date of Birth * (YYYY-MM-DD)", "Gender *",
                "Phone * (10 digits)", "Email *", "Password *", "Tier *", "Package *"};
        Component[] inputs = {txtName, txtDob, cbGender, txtPhone, txtEmail,
                txtPass, cbTier, cbPackage};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.3;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.7;
            panel.add(inputs[i], c);
        }

        JButton btnSave = new JButton("Add Member");
        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        btnSave.addActionListener(e -> {
            try {
                Member m = new Member();
                m.setFullName(txtName.getText().trim());
                m.setDateOfBirth(java.time.LocalDate.parse(txtDob.getText().trim()));
                m.setGender((String) cbGender.getSelectedItem());
                m.setPhone(txtPhone.getText().trim());
                m.setEmail(txtEmail.getText().trim());
                m.setPassword(new String(txtPass.getPassword()));
                // Validation, hashing, membership and payment creation handled in service
                memberService.registerMember(m,
                        (String) cbTier.getSelectedItem(),
                        (String) cbPackage.getSelectedItem(),
                        manager.getManagerId());
                JOptionPane.showMessageDialog(panel, "Member added successfully!");
                loadMembers();
                // Clear form fields after successful add
                txtName.setText(""); txtDob.setText("YYYY-MM-DD");
                txtPhone.setText(""); txtEmail.setText(""); txtPass.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    // --- Requests Tab ---

    // Builds the requests tab with two sub-tabs: registrations and tier upgrades
    private JPanel buildRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane subTabs = new JTabbedPane();
        subTabs.addTab("Registration Requests", buildRegistrationRequestsPanel());
        subTabs.addTab("Tier Upgrades",         buildTierUpgradeRequestsPanel());
        panel.add(subTabs, BorderLayout.CENTER);
        return panel;
    }

    // Builds the registration request approval panel
    // Calls expireOldRequests() on load to ensure expired requests are marked before display
    private JPanel buildRegistrationRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"Req ID", "Member ID", "Type", "Tier", "Package", "Amount", "Status", "Expires"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Runnable used to refresh table data — reused by buttons and initial load
        Runnable load = () -> {
            model.setRowCount(0);
            // Expire outdated requests before displaying
            memberService.expireOldRequests();
            for (RegistrationRequest r : memberService.getAllRegistrations()) {
                model.addRow(new Object[]{
                        r.getRequestId(), r.getMemberId(), r.getType(), r.getTier(),
                        r.getPackageType(), String.format("%.2f TL", r.getAmount()),
                        r.getStatus(), r.getExpiresAt().toLocalDate()
                });
            }
        };
        load.run();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnApprove = new JButton("Approve");
        JButton btnReject  = new JButton("Reject");
        JButton btnRefresh = new JButton("Refresh");

        btnApprove.setBackground(new Color(33, 120, 80));
        btnApprove.setForeground(Color.WHITE); btnApprove.setOpaque(true); btnApprove.setBorderPainted(false);
        btnReject.setBackground(new Color(150, 50, 50));
        btnReject.setForeground(Color.WHITE); btnReject.setOpaque(true); btnReject.setBorderPainted(false);

        toolbar.add(btnRefresh); toolbar.add(btnApprove); toolbar.add(btnReject);
        panel.add(toolbar, BorderLayout.NORTH);

        btnRefresh.addActionListener(e -> load.run());
        btnApprove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select a request."); return; }
            // Approval creates membership and payment records in service layer
            memberService.approveRegistration((int) model.getValueAt(row, 0), manager.getManagerId());
            JOptionPane.showMessageDialog(panel, "Registration approved.");
            load.run();
        });
        btnReject.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select a request."); return; }
            memberService.rejectRegistration((int) model.getValueAt(row, 0));
            JOptionPane.showMessageDialog(panel, "Registration rejected.");
            load.run();
        });

        return panel;
    }

    // Builds the tier upgrade request approval panel
    private JPanel buildTierUpgradeRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"Req ID", "Member ID", "Old Tier", "New Tier", "Fee", "Status", "Expires"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        Runnable load = () -> {
            model.setRowCount(0);
            for (TierUpgradeRequest r : memberService.getAllTierUpgrades()) {
                model.addRow(new Object[]{
                        r.getRequestId(), r.getMemberId(), r.getOldTier(), r.getNewTier(),
                        String.format("%.2f TL", r.getUpgradeFee()),
                        r.getStatus(), r.getExpiresAt().toLocalDate()
                });
            }
        };
        load.run();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnApprove = new JButton("Approve");
        JButton btnReject  = new JButton("Reject");
        JButton btnRefresh = new JButton("Refresh");

        btnApprove.setBackground(new Color(33, 120, 80));
        btnApprove.setForeground(Color.WHITE); btnApprove.setOpaque(true); btnApprove.setBorderPainted(false);
        btnReject.setBackground(new Color(150, 50, 50));
        btnReject.setForeground(Color.WHITE); btnReject.setOpaque(true); btnReject.setBorderPainted(false);

        toolbar.add(btnRefresh); toolbar.add(btnApprove); toolbar.add(btnReject);
        panel.add(toolbar, BorderLayout.NORTH);

        btnRefresh.addActionListener(e -> load.run());
        btnApprove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select a request."); return; }
            // Approval updates tier, package type, end date, and records payment in service
            memberService.approveTierUpgrade((int) model.getValueAt(row, 0), manager.getManagerId());
            JOptionPane.showMessageDialog(panel, "Tier upgrade approved.");
            load.run();
        });
        btnReject.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select a request."); return; }
            memberService.failTierUpgrade((int) model.getValueAt(row, 0));
            JOptionPane.showMessageDialog(panel, "Tier upgrade rejected.");
            load.run();
        });

        return panel;
    }

    // --- Trainers & PT Tab ---

    // Builds the Trainers & PT tab with two sub-tabs: trainer list and all appointments
    private JPanel buildTrainersPTPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane subTabs = new JTabbedPane();
        subTabs.addTab("Trainers",     buildTrainersSubPanel());
        subTabs.addTab("Appointments", buildAppointmentsSubPanel());
        panel.add(subTabs, BorderLayout.CENTER);
        return panel;
    }

    // Builds the trainer management sub-panel
    // Supports: add, edit, activate/deactivate, unlock trainer accounts
    private JPanel buildTrainersSubPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"ID", "Full Name", "Username", "Specialty", "Active", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        PTService ptService = new PTService();

        Runnable loadTrainers = () -> {
            model.setRowCount(0);
            for (Trainer t : ptService.getAllTrainers()) {
                model.addRow(new Object[]{
                        t.getTrainerId(), t.getFullName(), t.getUsername(),
                        t.getSpecialty() != null ? t.getSpecialty() : "-",
                        t.isActive() ? "Active" : "Inactive",
                        t.isLocked() ? "Locked" : "OK"
                });
            }
        };

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh  = new JButton("Refresh");
        JButton btnAdd      = new JButton("Add Trainer");
        JButton btnEdit     = new JButton("Edit Trainer");
        JButton btnActivate = new JButton("Activate / Deactivate");
        JButton btnUnlock   = new JButton("Unlock Account");

        btnAdd.setBackground(new Color(33, 120, 80));
        btnAdd.setForeground(Color.WHITE); btnAdd.setOpaque(true); btnAdd.setBorderPainted(false);
        btnEdit.setBackground(new Color(33, 87, 141));
        btnEdit.setForeground(Color.WHITE); btnEdit.setOpaque(true); btnEdit.setBorderPainted(false);
        btnActivate.setBackground(new Color(180, 130, 0));
        btnActivate.setForeground(Color.WHITE); btnActivate.setOpaque(true); btnActivate.setBorderPainted(false);

        toolbar.add(btnRefresh);
        toolbar.add(btnAdd);
        toolbar.add(btnEdit);
        toolbar.add(btnActivate);
        toolbar.add(btnUnlock);
        panel.add(toolbar, BorderLayout.NORTH);
        loadTrainers.run();

        btnRefresh.addActionListener(e -> loadTrainers.run());

        // Add trainer form shown in a JOptionPane dialog
        btnAdd.addActionListener(e -> {
            JTextField txtName      = new JTextField(20);
            JTextField txtUsername  = new JTextField(20);
            JTextField txtEmail     = new JTextField(20);
            JPasswordField txtPass  = new JPasswordField(20);
            JTextField txtSpecialty = new JTextField(20);

            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Full Name *:"));  form.add(txtName);
            form.add(new JLabel("Username *:"));   form.add(txtUsername);
            form.add(new JLabel("Email:"));        form.add(txtEmail);
            form.add(new JLabel("Password *:"));   form.add(txtPass);
            form.add(new JLabel("Specialty:"));    form.add(txtSpecialty);

            int result = JOptionPane.showConfirmDialog(panel, form,
                    "Add Trainer", JOptionPane.OK_CANCEL_OPTION);
            if (result != JOptionPane.OK_OPTION) return;

            String name = txtName.getText().trim();
            String user = txtUsername.getText().trim();
            String pass = new String(txtPass.getPassword());

            if (name.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please fill in all required fields (*)."); return;
            }
            if (pass.length() < 8) {
                JOptionPane.showMessageDialog(panel, "Password must be at least 8 characters.",
                        "Validation Error", JOptionPane.WARNING_MESSAGE); return;
            }
            try {
                Trainer t = new Trainer();
                t.setFullName(name); t.setUsername(user);
                t.setEmail(txtEmail.getText().trim());
                t.setPassword(pass);
                t.setSpecialty(txtSpecialty.getText().trim());
                t.setActive(true);
                // Password hashing handled in PTService.addTrainer()
                ptService.addTrainer(t);
                JOptionPane.showMessageDialog(panel, "Trainer added successfully!");
                loadTrainers.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select a trainer first."); return; }
            int trainerId = (int) model.getValueAt(row, 0);
            String name   = (String) model.getValueAt(row, 1);
            showEditTrainerDialog(trainerId, name, ptService, loadTrainers);
        });

        // Toggle active status — if currently Active → deactivate, if Inactive → activate
        btnActivate.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select a trainer."); return; }
            int trainerId    = (int) model.getValueAt(row, 0);
            String activeStr = (String) model.getValueAt(row, 4);
            ptService.setTrainerActive(trainerId, "Inactive".equals(activeStr));
            loadTrainers.run();
        });

        btnUnlock.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select a trainer."); return; }
            ptService.unlockTrainer((int) model.getValueAt(row, 0));
            JOptionPane.showMessageDialog(panel, "Trainer account unlocked.");
            loadTrainers.run();
        });

        return panel;
    }

    // Builds the all-appointments sub-panel — read-only view of all PT appointments
    // Iterates over all trainers and fetches their appointments
    private JPanel buildAppointmentsSubPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        PTService ptService = new PTService();

        String[] cols = {"ID", "Member", "Trainer", "Date", "Start", "End", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        Runnable load = () -> {
            model.setRowCount(0);
            for (Trainer t : ptService.getAllTrainers()) {
                for (PersonalTrainingAppointment apt : ptService.getTrainerAppointments(t.getTrainerId())) {
                    // Resolve member name from ID — fallback to ID if not found
                    String memberName = memberService.getMemberById(apt.getMemberId())
                            .map(Member::getFullName).orElse("ID:" + apt.getMemberId());
                    model.addRow(new Object[]{
                            apt.getAppointmentId(), memberName, t.getFullName(),
                            apt.getAppointmentDate(), apt.getStartTime(),
                            apt.getEndTime(), apt.getStatus()
                    });
                }
            }
        };
        load.run();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh = new JButton("Refresh");
        toolbar.add(btnRefresh);
        panel.add(toolbar, BorderLayout.NORTH);
        btnRefresh.addActionListener(e -> load.run());

        return panel;
    }

    // --- Edit Trainer Dialog ---

    // Opens a dialog to edit trainer info and set working days
    // Password field is optional — leave blank to keep existing password
    private void showEditTrainerDialog(int trainerId, String trainerName,
                                       PTService ptService, Runnable onSave) {
        JDialog dialog = new JDialog(this, "Edit Trainer", true);
        dialog.setSize(420, 280);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Pre-fill fields with current trainer data
        Trainer current = ptService.getAllTrainers().stream()
                .filter(t -> t.getTrainerId() == trainerId)
                .findFirst().orElse(new Trainer());

        JTextField txtName      = new JTextField(current.getFullName(), 20);
        JTextField txtUsername  = new JTextField(current.getUsername() != null ? current.getUsername() : "", 20);
        JPasswordField txtPass  = new JPasswordField(20);
        JTextField txtSpecialty = new JTextField(current.getSpecialty() != null ? current.getSpecialty() : "", 20);

        String[] labels    = {"Full Name:", "Username:", "New Password:", "Specialty:"};
        Component[] inputs = {txtName, txtUsername, txtPass, txtSpecialty};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.4;
            panel.add(new JLabel(labels[i]), c);
            c.gridx = 1; c.weightx = 0.6;
            panel.add(inputs[i], c);
        }

        JButton btnSave        = new JButton("Save");
        JButton btnWorkingDays = new JButton("Set Working Days");

        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE); btnSave.setOpaque(true); btnSave.setBorderPainted(false);
        btnWorkingDays.setBackground(new Color(33, 120, 80));
        btnWorkingDays.setForeground(Color.WHITE); btnWorkingDays.setOpaque(true); btnWorkingDays.setBorderPainted(false);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        btnPanel.add(btnSave);
        btnPanel.add(btnWorkingDays);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnPanel, c);
        dialog.add(panel);

        btnSave.addActionListener(e -> {
            try {
                String pass = new String(txtPass.getPassword());
                // Pass null for password to keep existing — service handles this
                ptService.updateTrainerInfo(trainerId,
                        txtName.getText().trim(),
                        txtUsername.getText().trim(),
                        txtSpecialty.getText().trim(),
                        pass.isEmpty() ? null : pass);
                JOptionPane.showMessageDialog(dialog, "Trainer updated successfully.");
                onSave.run();
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Opens the working days dialog as a child of this dialog
        btnWorkingDays.addActionListener(e ->
                showWorkingDaysDialog(dialog, trainerId, trainerName, ptService));

        dialog.setVisible(true);
    }

    // --- Working Days Dialog ---

    // Opens a dialog to configure a trainer's working schedule for each day of the week
    // Pre-fills checkboxes and time fields with existing working day data
    private void showWorkingDaysDialog(JDialog parent, int trainerId,
                                       String trainerName, PTService ptService) {
        JDialog dialog = new JDialog(parent, "Working Days — " + trainerName, true);
        dialog.setSize(460, 380);
        dialog.setLocationRelativeTo(parent);

        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                "FRIDAY", "SATURDAY", "SUNDAY"};

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Header row
        c.gridy = 0;
        c.gridx = 1; panel.add(new JLabel("Day"), c);
        c.gridx = 2; panel.add(new JLabel("Start (HH:MM)"), c);
        c.gridx = 3; panel.add(new JLabel("End (HH:MM)"), c);

        JCheckBox[] checks = new JCheckBox[days.length];
        JTextField[] starts = new JTextField[days.length];
        JTextField[] ends   = new JTextField[days.length];

        // Load existing working days into a map for quick lookup
        Map<String, TrainerWorkingDay> existing = new HashMap<>();
        for (TrainerWorkingDay wd : ptService.getWorkingDays(trainerId))
            existing.put(wd.getDayOfWeek(), wd);

        for (int i = 0; i < days.length; i++) {
            TrainerWorkingDay wd = existing.get(days[i]);
            checks[i] = new JCheckBox();
            // Pre-fill times from existing data or use defaults
            starts[i] = new JTextField(wd != null ? wd.getStartTime().toString() : "09:00", 6);
            ends[i]   = new JTextField(wd != null ? wd.getEndTime().toString()   : "18:00", 6);

            if (wd != null) checks[i].setSelected(true);
            starts[i].setEnabled(wd != null);
            ends[i].setEnabled(wd != null);

            // Enable/disable time fields when checkbox is toggled
            int idx = i;
            checks[i].addActionListener(e -> {
                starts[idx].setEnabled(checks[idx].isSelected());
                ends[idx].setEnabled(checks[idx].isSelected());
            });

            c.gridy = i + 1;
            c.gridx = 0; c.weightx = 0.1; panel.add(checks[i], c);
            c.gridx = 1; c.weightx = 0.4; panel.add(new JLabel(days[i]), c);
            c.gridx = 2; c.weightx = 0.25; panel.add(starts[i], c);
            c.gridx = 3; c.weightx = 0.25; panel.add(ends[i], c);
        }

        JButton btnSave        = new JButton("Save Working Days");
        JButton btnLessonSlots = new JButton("Set Lesson Slots");

        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE); btnSave.setOpaque(true); btnSave.setBorderPainted(false);
        btnLessonSlots.setBackground(new Color(80, 50, 120));
        btnLessonSlots.setForeground(Color.WHITE); btnLessonSlots.setOpaque(true); btnLessonSlots.setBorderPainted(false);

        JPanel bottom = new JPanel(new GridLayout(1, 2, 8, 0));
        bottom.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        bottom.add(btnSave);
        bottom.add(btnLessonSlots);

        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(panel), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);

        // Save selected working days using delete + insert via PTService
        btnSave.addActionListener(e -> {
            try {
                List<TrainerWorkingDay> list = new ArrayList<>();
                for (int i = 0; i < days.length; i++) {
                    if (checks[i].isSelected()) {
                        LocalTime s  = LocalTime.parse(starts[i].getText().trim());
                        LocalTime en = LocalTime.parse(ends[i].getText().trim());
                        TrainerWorkingDay wd = new TrainerWorkingDay();
                        wd.setTrainerId(trainerId);
                        wd.setDayOfWeek(days[i]);
                        wd.setStartTime(s);
                        wd.setEndTime(en);
                        list.add(wd);
                    }
                }
                ptService.saveWorkingDays(trainerId, list);
                JOptionPane.showMessageDialog(dialog, "Working days saved successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Opens lesson slots dialog — passes current checkbox/time state to avoid re-reading from DB
        btnLessonSlots.addActionListener(e ->
                showLessonSlotsDialog(dialog, trainerId, trainerName, ptService, days, checks, starts, ends));

        dialog.setVisible(true);
    }

    // --- Lesson Slots Dialog ---

    // Opens a dialog to manage lesson slots for a trainer
    // Only allows slots on days marked as working days in the working days dialog
    // Slots are added to a table and saved all at once via PTService
    private void showLessonSlotsDialog(JDialog parent, int trainerId, String trainerName,
                                       PTService ptService,
                                       String[] days, JCheckBox[] checks,
                                       JTextField[] starts, JTextField[] ends) {

        // Build list of active days and their working hours from the working days dialog
        List<String> activeDaysList = new ArrayList<>();
        Map<String, String[]> workingHours = new HashMap<>();
        for (int i = 0; i < days.length; i++) {
            if (checks[i].isSelected()) {
                activeDaysList.add(days[i]);
                workingHours.put(days[i], new String[]{
                        starts[i].getText().trim(),
                        ends[i].getText().trim()
                });
            }
        }

        // Cannot add slots if no working days are selected
        if (activeDaysList.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "Please select at least one working day first.",
                    "No Working Days", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(parent, "Lesson Slots — " + trainerName, true);
        dialog.setSize(560, 500);
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout(5, 5));

        // Table showing all current lesson slots
        String[] cols = {"ID", "Day", "Start", "End"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        // Load existing slots from DB into table
        List<TrainerLessonSlot> existingSlots = ptService.getLessonSlots(trainerId);
        for (TrainerLessonSlot slot : existingSlots) {
            model.addRow(new Object[]{
                    slot.getSlotId(),
                    slot.getDayOfWeek(),
                    slot.getStartTime().toString(),
                    slot.getEndTime().toString()
            });
        }

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Current Lesson Slots"));
        tablePanel.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(tablePanel, BorderLayout.CENTER);

        // Dropdown shows active days with their working hour ranges
        String[] dayLabels = activeDaysList.stream().map(d -> {
            String[] h = workingHours.get(d);
            return d + "  (" + h[0] + " – " + h[1] + ")";
        }).toArray(String[]::new);

        JComboBox<String> cbDay = new JComboBox<>(dayLabels);
        JTextField txtStart     = new JTextField("09:00", 7);
        JTextField txtEnd       = new JTextField("10:00", 7);
        JButton btnAdd          = new JButton("Add Slot");
        JButton btnDelete       = new JButton("Delete Selected");

        btnAdd.setBackground(new Color(33, 120, 80));
        btnAdd.setForeground(Color.WHITE); btnAdd.setOpaque(true); btnAdd.setBorderPainted(false);
        btnDelete.setBackground(new Color(150, 50, 50));
        btnDelete.setForeground(Color.WHITE); btnDelete.setOpaque(true); btnDelete.setBorderPainted(false);

        JPanel addPanel = new JPanel(new GridBagLayout());
        addPanel.setBorder(BorderFactory.createTitledBorder("Add New Slot"));
        GridBagConstraints ac = new GridBagConstraints();
        ac.insets = new Insets(6, 8, 6, 8);
        ac.fill = GridBagConstraints.HORIZONTAL;

        ac.gridx = 0; ac.gridy = 0; ac.weightx = 0.3; addPanel.add(new JLabel("Day:"), ac);
        ac.gridx = 1; ac.weightx = 0.7; ac.gridwidth = 3; addPanel.add(cbDay, ac);
        ac.gridwidth = 1;
        ac.gridx = 0; ac.gridy = 1; ac.weightx = 0.3; addPanel.add(new JLabel("Start (HH:MM):"), ac);
        ac.gridx = 1; ac.weightx = 0.7; addPanel.add(txtStart, ac);
        ac.gridx = 0; ac.gridy = 2; ac.weightx = 0.3; addPanel.add(new JLabel("End (HH:MM):"), ac);
        ac.gridx = 1; ac.weightx = 0.7; addPanel.add(txtEnd, ac);
        ac.gridx = 0; ac.gridy = 3; ac.gridwidth = 1; addPanel.add(btnAdd, ac);
        ac.gridx = 1; addPanel.add(btnDelete, ac);

        dialog.add(addPanel, BorderLayout.SOUTH);

        // Adds a new slot row to the table — format validation only, business logic on save
        btnAdd.addActionListener(e -> {
            try {
                String selectedDay = activeDaysList.get(cbDay.getSelectedIndex());
                // Parse for format validation — exceptions caught if invalid
                LocalTime.parse(txtStart.getText().trim());
                LocalTime.parse(txtEnd.getText().trim());
                model.addRow(new Object[]{"-", selectedDay,
                        txtStart.getText().trim(), txtEnd.getText().trim()});
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid time format. Use HH:MM.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Removes the selected row from the table
        btnDelete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) model.removeRow(row);
            else JOptionPane.showMessageDialog(dialog, "Please select a slot to delete.");
        });

        // Saves all slots in the table to the database via PTService (delete + insert)
        JButton btnSave = new JButton("Save All Slots");
        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE); btnSave.setOpaque(true); btnSave.setBorderPainted(false);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(btnSave);
        dialog.add(bottom, BorderLayout.NORTH);

        btnSave.addActionListener(e -> {
            try {
                List<TrainerLessonSlot> list = new ArrayList<>();
                for (int i = 0; i < model.getRowCount(); i++) {
                    TrainerLessonSlot slot = new TrainerLessonSlot();
                    slot.setTrainerId(trainerId);
                    slot.setDayOfWeek((String) model.getValueAt(i, 1));
                    slot.setStartTime(LocalTime.parse((String) model.getValueAt(i, 2)));
                    slot.setEndTime(LocalTime.parse((String) model.getValueAt(i, 3)));
                    list.add(slot);
                }
                // PTService.saveLessonSlots() uses delete + insert pattern
                ptService.saveLessonSlots(trainerId, list);
                JOptionPane.showMessageDialog(dialog, "Lesson slots saved successfully.");
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.setVisible(true);
    }
}