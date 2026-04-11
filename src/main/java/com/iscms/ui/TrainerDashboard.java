package com.iscms.ui;

import com.iscms.model.*;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

// Trainer dashboard — accessible by users with TRAINER role
// Provides 3 tabs: My Appointments, My Profile, My Schedule
public class TrainerDashboard extends JFrame {

    // The currently logged-in trainer
    private final Trainer trainer;

    // Service instances — no DAOs directly in UI layer
    private final PTService ptService         = new PTService();
    private final MemberService memberService = new MemberService();

    // Table model for the appointments tab — shared for refresh access
    private DefaultTableModel aptModel;

    public TrainerDashboard(Trainer trainer) {
        this.trainer = trainer;
        setTitle("Trainer Panel — " + trainer.getFullName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 620);
        setLocationRelativeTo(null);
        initUI();
    }

    // Builds the main layout: top bar + tabbed pane with 3 tabs
    private void initUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(80, 50, 120));
        topBar.setPreferredSize(new Dimension(0, 50));

        // Show trainer name and specialty in the top bar
        JLabel lblTitle = new JLabel("  ISC-MS | Trainer: " + trainer.getFullName()
                + (trainer.getSpecialty() != null ? " (" + trainer.getSpecialty() + ")" : ""));
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

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("My Appointments", buildAppointmentsPanel());
        tabs.addTab("My Profile",      buildProfilePanel());
        tabs.addTab("My Schedule",     buildSchedulePanel());
        add(tabs, BorderLayout.CENTER);
    }

    // --- My Appointments Tab ---

    // Builds the appointments panel with complete and no-show actions
    private JPanel buildAppointmentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh  = new JButton("Refresh");
        JButton btnComplete = new JButton("Mark Completed");
        JButton btnNoShow   = new JButton("Mark No-Show");

        btnComplete.setBackground(new Color(50, 150, 50));
        btnComplete.setForeground(Color.WHITE);
        btnComplete.setOpaque(true);
        btnComplete.setBorderPainted(false);

        btnNoShow.setBackground(new Color(150, 50, 50));
        btnNoShow.setForeground(Color.WHITE);
        btnNoShow.setOpaque(true);
        btnNoShow.setBorderPainted(false);

        toolbar.add(btnRefresh);
        toolbar.add(btnComplete);
        toolbar.add(btnNoShow);
        panel.add(toolbar, BorderLayout.NORTH);

        // Non-editable appointment table
        String[] cols = {"ID", "Member Name", "Date", "Start", "End", "Status"};
        aptModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(aptModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> loadAppointments());

        // Mark selected appointment as COMPLETED
        btnComplete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select an appointment."); return; }
            ptService.markCompleted((int) aptModel.getValueAt(row, 0));
            loadAppointments();
        });

        // Mark selected appointment as NO_SHOW — applies 7-day booking penalty to member
        btnNoShow.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(panel, "Please select an appointment."); return; }
            ptService.markNoShow((int) aptModel.getValueAt(row, 0));
            JOptionPane.showMessageDialog(panel,
                    "No-show recorded. 7-day penalty applied to member.");
            loadAppointments();
        });

        loadAppointments();
        return panel;
    }

    // Loads all appointments for this trainer and resolves member names
    private void loadAppointments() {
        aptModel.setRowCount(0);
        for (PersonalTrainingAppointment apt :
                ptService.getTrainerAppointments(trainer.getTrainerId())) {
            // Resolve member name from ID — fallback to "Unknown" if not found
            String memberName = memberService.getMemberById(apt.getMemberId())
                    .map(Member::getFullName).orElse("Unknown");
            aptModel.addRow(new Object[]{
                    apt.getAppointmentId(), memberName,
                    apt.getAppointmentDate(), apt.getStartTime(),
                    apt.getEndTime(), apt.getStatus()
            });
        }
    }

    // --- My Profile Tab ---

    // Builds the profile panel — trainer can only change their password
    // Full name and username are read-only
    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Read-only fields shown with grey background
        JTextField txtName = new JTextField(
                trainer.getFullName() != null ? trainer.getFullName() : "");
        txtName.setEditable(false);
        txtName.setBackground(new Color(240, 240, 240));

        JTextField txtUser = new JTextField(
                trainer.getUsername() != null ? trainer.getUsername() : "");
        txtUser.setEditable(false);
        txtUser.setBackground(new Color(240, 240, 240));

        JPasswordField txtPass    = new JPasswordField(20);
        JPasswordField txtConfirm = new JPasswordField(20);

        String[] labels    = {"Full Name (read-only)", "Username (read-only)",
                "New Password", "Confirm Password"};
        Component[] inputs = {txtName, txtUser, txtPass, txtConfirm};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.3;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.7;
            panel.add(inputs[i], c);
        }

        JButton btnSave = new JButton("Save Password");
        btnSave.setBackground(new Color(80, 50, 120));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        btnSave.addActionListener(e -> {
            String pass    = new String(txtPass.getPassword());
            String confirm = new String(txtConfirm.getPassword());

            // Validation: password must not be empty
            if (pass.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Please enter a new password.");
                return;
            }

            // Validation: both password fields must match
            if (!pass.equals(confirm)) {
                JOptionPane.showMessageDialog(panel, "Passwords do not match.");
                return;
            }

            // Delegate to PTService — BCrypt hashing applied before storing
            ptService.resetTrainerPassword(trainer.getTrainerId(), pass);
            JOptionPane.showMessageDialog(panel, "Password updated successfully.");
            txtPass.setText("");
            txtConfirm.setText("");
        });

        return panel;
    }

    // --- My Schedule Tab ---

    // Builds the schedule panel showing working days and lesson slots in a split view
    // Read-only — trainers cannot edit their schedule from this panel (manager does that)
    private JPanel buildSchedulePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        // Working days table — shows general working hours per day
        String[] wdCols = {"Day", "Working Start", "Working End"};
        DefaultTableModel wdModel = new DefaultTableModel(wdCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (TrainerWorkingDay wd : ptService.getWorkingDays(trainer.getTrainerId())) {
            wdModel.addRow(new Object[]{
                    wd.getDayOfWeek(), wd.getStartTime(), wd.getEndTime()
            });
        }

        // Lesson slots table — shows specific bookable time slots per day
        String[] slotCols = {"Day", "Lesson Start", "Lesson End"};
        DefaultTableModel slotModel = new DefaultTableModel(slotCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (TrainerLessonSlot s : ptService.getLessonSlots(trainer.getTrainerId())) {
            slotModel.addRow(new Object[]{
                    s.getDayOfWeek(), s.getStartTime(), s.getEndTime()
            });
        }

        // Split view: working hours on top, lesson slots on bottom
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(new JTable(wdModel)),
                new JScrollPane(new JTable(slotModel)));
        split.setDividerLocation(200);

        panel.add(new JLabel("Working Hours / Lesson Slots", SwingConstants.CENTER),
                BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }
}