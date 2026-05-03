package com.iscms.ui;

import com.iscms.model.*;
import com.iscms.service.AuthService;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalTime;
import java.util.*;
import java.util.List;

// Manager dashboard — accessible by users with MANAGER role
// Provides 7 tabs: Members, Add Member, Requests, Events, Trainers & PT, Reports, My Profile
public class ManagerDashboard extends JFrame {

    private final Manager manager;

    private final MemberService memberService = new MemberService();
    private final AuthService   authService   = new AuthService();

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

    private void initUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(33, 87, 141));
        topBar.setPreferredSize(new Dimension(0, 50));

        JLabel lblTitle = new JLabel("  ISC-MS | Manager: " + manager.getFullName());
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
        tabs.addTab("Members",       buildMembersPanel());
        tabs.addTab("Add Member",    buildAddMemberPanel());
        tabs.addTab("Requests",      buildRequestsPanel());
        tabs.addTab("Events",        new EventManagementPanel(manager));
        tabs.addTab("Trainers & PT", buildTrainersPTPanel());
        tabs.addTab("Reports",       new ReportsPanel());
        tabs.addTab("My Profile",    buildMyProfilePanel());
        add(tabs, BorderLayout.CENTER);
    }

    // --- Members Tab ---

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

        String[] cols = {"ID", "Full Name", "Phone", "Email", "Status", "Tier", "Created"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        memberTable = new JTable(tableModel);
        memberTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        memberTable.getTableHeader().setReorderingAllowed(false);
        memberTable.removeColumn(memberTable.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(memberTable), BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> loadMembers());
        btnSearch.addActionListener(e  -> searchMembers());
        btnSuspend.addActionListener(e -> suspendMember());
        btnUnlock.addActionListener(e  -> unlockMember());

        return panel;
    }

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

    private void unlockMember() {
        int row = memberTable.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Please select a member."); return; }
        memberService.unlockMember((int) tableModel.getValueAt(row, 0));
        JOptionPane.showMessageDialog(this, "Member unlocked successfully.");
        loadMembers();
    }

    // --- Add Member Tab ---
    // Manager registers a member directly with cash payment. ANNUAL_INSTALLMENT
    // is intentionally not offered here — installment requires the online flow
    // (only the member can self-register with online payment). Cash + installment
    // would create an installment schedule with no online "Pay Now" path for
    // future months, which doesn't match the rest of the system.

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
        // ANNUAL_INSTALLMENT removed from this combo — online flow only
        JComboBox<String> cbPackage = new JComboBox<>(
                new String[]{"MONTHLY", "ANNUAL_PREPAID"});

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

        JLabel lblHint = new JLabel(
                "<html><i>Note: For installment plans, the member must self-register online.</i></html>");
        lblHint.setForeground(new Color(100, 100, 100));
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(lblHint, c);

        JButton btnSave = new JButton("Add Member");
        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridy = inputs.length + 1;
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
                memberService.registerMember(m,
                        (String) cbTier.getSelectedItem(),
                        (String) cbPackage.getSelectedItem(),
                        manager.getManagerId());
                JOptionPane.showMessageDialog(panel, "Member added successfully!");
                loadMembers();
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

    private JPanel buildRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane subTabs = new JTabbedPane();
        subTabs.addTab("Registration Requests", buildRegistrationRequestsPanel());
        subTabs.addTab("Tier Upgrades",         buildTierUpgradeRequestsPanel());
        panel.add(subTabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRegistrationRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"ReqID", "Member", "Type", "Tier", "Package", "Amount", "Status", "Expires"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        Runnable load = () -> {
            model.setRowCount(0);
            memberService.expireOldRequests();
            for (RegistrationRequest r : memberService.getAllRegistrations()) {
                String memberName = memberService.getMemberById(r.getMemberId())
                        .map(Member::getFullName).orElse("(deleted)");
                model.addRow(new Object[]{
                        r.getRequestId(), memberName, r.getType(), r.getTier(),
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
            try {
                memberService.approveRegistration((int) model.getValueAt(row, 0), manager.getManagerId());
                JOptionPane.showMessageDialog(panel, "Registration approved.");
                load.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
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

    private JPanel buildTierUpgradeRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] cols = {"ReqID", "Member", "Old Tier", "New Tier", "Fee", "Status", "Expires"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        Runnable load = () -> {
            model.setRowCount(0);
            for (TierUpgradeRequest r : memberService.getAllTierUpgrades()) {
                String memberName = memberService.getMemberById(r.getMemberId())
                        .map(Member::getFullName).orElse("(deleted)");
                model.addRow(new Object[]{
                        r.getRequestId(), memberName, r.getOldTier(), r.getNewTier(),
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
            try {
                memberService.approveTierUpgrade((int) model.getValueAt(row, 0), manager.getManagerId());
                JOptionPane.showMessageDialog(panel, "Tier upgrade approved.");
                load.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
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

    private JPanel buildTrainersPTPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTabbedPane subTabs = new JTabbedPane();
        subTabs.addTab("Trainers",     buildTrainersSubPanel());
        subTabs.addTab("Appointments", buildAppointmentsSubPanel());
        panel.add(subTabs, BorderLayout.CENTER);
        return panel;
    }

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
        table.removeColumn(table.getColumnModel().getColumn(0));
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

        btnAdd.addActionListener(e -> {
            JTextField txtName      = new JTextField(20);
            JTextField txtUsername  = new JTextField(20);
            JTextField txtEmail     = new JTextField(20);
            JPasswordField txtPass  = new JPasswordField(20);
            JTextField txtSpecialty = new JTextField(20);

            JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
            form.add(new JLabel("Full Name *:"));         form.add(txtName);
            form.add(new JLabel("Username *:"));          form.add(txtUsername);
            form.add(new JLabel("Email:"));               form.add(txtEmail);
            form.add(new JLabel("Initial Password *:"));  form.add(txtPass);
            form.add(new JLabel("Specialty:"));           form.add(txtSpecialty);

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

    private JPanel buildAppointmentsSubPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        PTService ptService = new PTService();

        String[] cols = {"AptID", "Member", "Trainer", "Date", "Start", "End", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(model);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        Runnable load = () -> {
            model.setRowCount(0);
            for (Trainer t : ptService.getAllTrainers()) {
                for (PersonalTrainingAppointment apt : ptService.getTrainerAppointments(t.getTrainerId())) {
                    String memberName = memberService.getMemberById(apt.getMemberId())
                            .map(Member::getFullName).orElse("(deleted)");
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

    private void showEditTrainerDialog(int trainerId, String trainerName,
                                       PTService ptService, Runnable onSave) {
        JDialog dialog = new JDialog(this, "Edit Trainer", true);
        dialog.setSize(420, 260);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        Trainer current = ptService.getAllTrainers().stream()
                .filter(t -> t.getTrainerId() == trainerId)
                .findFirst().orElse(new Trainer());

        JTextField txtName      = new JTextField(current.getFullName(), 20);
        JTextField txtUsername  = new JTextField(current.getUsername() != null ? current.getUsername() : "", 20);
        JTextField txtSpecialty = new JTextField(current.getSpecialty() != null ? current.getSpecialty() : "", 20);

        String[] labels    = {"Full Name:", "Username:", "Specialty:"};
        Component[] inputs = {txtName, txtUsername, txtSpecialty};

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
                ptService.updateTrainerInfo(trainerId,
                        txtName.getText().trim(),
                        txtUsername.getText().trim(),
                        txtSpecialty.getText().trim(),
                        null);
                JOptionPane.showMessageDialog(dialog, "Trainer updated successfully.");
                onSave.run();
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnWorkingDays.addActionListener(e ->
                showWorkingDaysDialog(dialog, trainerId, trainerName, ptService));

        dialog.setVisible(true);
    }

    // --- Working Days Dialog ---

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

        c.gridy = 0;
        c.gridx = 1; panel.add(new JLabel("Day"), c);
        c.gridx = 2; panel.add(new JLabel("Start (HH:MM)"), c);
        c.gridx = 3; panel.add(new JLabel("End (HH:MM)"), c);

        JCheckBox[] checks = new JCheckBox[days.length];
        JTextField[] starts = new JTextField[days.length];
        JTextField[] ends   = new JTextField[days.length];

        Map<String, TrainerWorkingDay> existing = new HashMap<>();
        for (TrainerWorkingDay wd : ptService.getWorkingDays(trainerId))
            existing.put(wd.getDayOfWeek(), wd);

        for (int i = 0; i < days.length; i++) {
            TrainerWorkingDay wd = existing.get(days[i]);
            checks[i] = new JCheckBox();
            starts[i] = new JTextField(wd != null ? wd.getStartTime().toString() : "09:00", 6);
            ends[i]   = new JTextField(wd != null ? wd.getEndTime().toString()   : "18:00", 6);

            if (wd != null) checks[i].setSelected(true);
            starts[i].setEnabled(wd != null);
            ends[i].setEnabled(wd != null);

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
                JOptionPane.showMessageDialog(dialog, "Working days saved.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnLessonSlots.addActionListener(e ->
                showLessonSlotsDialog(dialog, trainerId, trainerName, ptService));

        dialog.setVisible(true);
    }

    // --- Lesson Slots Dialog ---

    private void showLessonSlotsDialog(JDialog parent, int trainerId,
                                       String trainerName, PTService ptService) {
        JDialog dialog = new JDialog(parent, "Lesson Slots — " + trainerName, true);
        dialog.setSize(560, 480);
        dialog.setLocationRelativeTo(parent);

        DefaultTableModel slotModel = new DefaultTableModel(
                new String[]{"ID", "Day", "Start", "End"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable slotTable = new JTable(slotModel);
        slotTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        slotTable.removeColumn(slotTable.getColumnModel().getColumn(0));

        Runnable loadSlots = () -> {
            slotModel.setRowCount(0);
            for (TrainerLessonSlot s : ptService.getLessonSlots(trainerId)) {
                slotModel.addRow(new Object[]{
                        s.getSlotId(), s.getDayOfWeek(), s.getStartTime(), s.getEndTime()
                });
            }
        };
        loadSlots.run();

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> cbDay = new JComboBox<>(new String[]{
                "MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"});
        JTextField txtStart = new JTextField("09:00", 6);
        JTextField txtEnd   = new JTextField("10:00", 6);
        JButton btnAdd      = new JButton("Add Slot");
        JButton btnRemove   = new JButton("Remove Selected");
        JButton btnSaveAll  = new JButton("Save All");

        btnSaveAll.setBackground(new Color(33, 87, 141));
        btnSaveAll.setForeground(Color.WHITE); btnSaveAll.setOpaque(true); btnSaveAll.setBorderPainted(false);

        c.gridy = 0;
        c.gridx = 0; formPanel.add(new JLabel("Day:"), c);
        c.gridx = 1; formPanel.add(cbDay, c);
        c.gridx = 2; formPanel.add(new JLabel("Start:"), c);
        c.gridx = 3; formPanel.add(txtStart, c);
        c.gridx = 4; formPanel.add(new JLabel("End:"), c);
        c.gridx = 5; formPanel.add(txtEnd, c);
        c.gridx = 6; formPanel.add(btnAdd, c);

        btnAdd.addActionListener(e -> {
            try {
                LocalTime s = LocalTime.parse(txtStart.getText().trim());
                LocalTime en = LocalTime.parse(txtEnd.getText().trim());
                slotModel.addRow(new Object[]{0, cbDay.getSelectedItem(), s, en});
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog,
                        "Invalid time format. Use HH:MM.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        btnRemove.addActionListener(e -> {
            int row = slotTable.getSelectedRow();
            if (row >= 0) slotModel.removeRow(row);
        });
        btnSaveAll.addActionListener(e -> {
            try {
                List<TrainerLessonSlot> slots = new ArrayList<>();
                for (int i = 0; i < slotModel.getRowCount(); i++) {
                    TrainerLessonSlot s = new TrainerLessonSlot();
                    s.setTrainerId(trainerId);
                    s.setDayOfWeek((String) slotModel.getValueAt(i, 1));
                    s.setStartTime((LocalTime) slotModel.getValueAt(i, 2));
                    s.setEndTime((LocalTime) slotModel.getValueAt(i, 3));
                    slots.add(s);
                }
                ptService.saveLessonSlots(trainerId, slots);
                JOptionPane.showMessageDialog(dialog, "Lesson slots saved.");
                loadSlots.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(btnRemove);
        actions.add(btnSaveAll);

        dialog.setLayout(new BorderLayout());
        dialog.add(formPanel, BorderLayout.NORTH);
        dialog.add(new JScrollPane(slotTable), BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // --- My Profile Tab ---

    private JPanel buildMyProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtName  = readOnly(manager.getFullName());
        JTextField txtUser  = readOnly(manager.getUsername() != null ? manager.getUsername() : "");
        JTextField txtEmail = readOnly(manager.getEmail() != null ? manager.getEmail() : "");
        JTextField txtRole  = readOnly(manager.getRole());

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
        btnSave.setBackground(new Color(33, 87, 141));
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

            if (!authService.verifyManagerPassword(manager.getManagerId(), current)) {
                JOptionPane.showMessageDialog(panel,
                        "Current password is incorrect.",
                        "Authentication Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }

            AuthService.ResetResult result =
                    authService.resetManagerPasswordByEmail(manager.getEmail(), pass);
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

    private JTextField readOnly(String value) {
        JTextField field = new JTextField(value);
        field.setEditable(false);
        field.setBackground(new Color(240, 240, 240));
        return field;
    }
}