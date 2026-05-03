package com.iscms.ui;

import com.iscms.model.*;
import com.iscms.service.AuthService;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

// Trainer dashboard — accessible by users with TRAINER role
// Provides 3 tabs: My Appointments, My Profile, My Schedule
public class TrainerDashboard extends JFrame {

    private final Trainer trainer;

    private final PTService ptService         = new PTService();
    private final MemberService memberService = new MemberService();
    private final AuthService authService     = new AuthService();

    private DefaultTableModel aptModel;

    public TrainerDashboard(Trainer trainer) {
        this.trainer = trainer;
        setTitle("Trainer Panel — " + trainer.getFullName());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 620);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(new Color(80, 50, 120));
        topBar.setPreferredSize(new Dimension(0, 50));

        JLabel lblTitle = new JLabel("  ISC-MS | Trainer: " + trainer.getFullName()
                + (trainer.getSpecialty() != null ? " (" + trainer.getSpecialty() + ")" : ""));
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
        tabs.addTab("My Appointments", buildAppointmentsPanel());
        tabs.addTab("My Profile",      buildProfilePanel());
        tabs.addTab("My Schedule",     buildSchedulePanel());
        add(tabs, BorderLayout.CENTER);
    }

    // --- My Appointments Tab ---
    // Mark Completed and Mark No-Show actions wrap their service calls in try/catch
    // so that business-rule violations from PTService surface as proper dialog
    // messages instead of silently failing on the EDT.

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

        // Appointment ID kept at model col 0, hidden in view
        String[] cols = {"AptID", "Member Name", "Date", "Start", "End", "Status"};
        aptModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(aptModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.removeColumn(table.getColumnModel().getColumn(0));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> loadAppointments());

        btnComplete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Please select an appointment.");
                return;
            }
            try {
                ptService.markCompleted((int) aptModel.getValueAt(row, 0));
                JOptionPane.showMessageDialog(panel, "Appointment marked as completed.");
                loadAppointments();
            } catch (Exception ex) {
                // Surfaces "future appointment", "already cancelled", etc. from PTService
                JOptionPane.showMessageDialog(panel, ex.getMessage(),
                        "Cannot Mark Completed", JOptionPane.WARNING_MESSAGE);
            }
        });

        btnNoShow.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Please select an appointment.");
                return;
            }
            try {
                ptService.markNoShow((int) aptModel.getValueAt(row, 0));
                JOptionPane.showMessageDialog(panel,
                        "No-show recorded. 7-day penalty applied to member.");
                loadAppointments();
            } catch (Exception ex) {
                // Surfaces grace-period violations, status guards, etc. from PTService
                JOptionPane.showMessageDialog(panel, ex.getMessage(),
                        "Cannot Mark No-Show", JOptionPane.WARNING_MESSAGE);
            }
        });

        loadAppointments();
        return panel;
    }

    private void loadAppointments() {
        aptModel.setRowCount(0);
        for (PersonalTrainingAppointment apt :
                ptService.getTrainerAppointments(trainer.getTrainerId())) {
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
    // Trainer changes their own password from here.
    // Requires current password verification before applying the new one.

    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(30, 60, 30, 60));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtName = new JTextField(
                trainer.getFullName() != null ? trainer.getFullName() : "");
        txtName.setEditable(false);
        txtName.setBackground(new Color(240, 240, 240));

        JTextField txtUser = new JTextField(
                trainer.getUsername() != null ? trainer.getUsername() : "");
        txtUser.setEditable(false);
        txtUser.setBackground(new Color(240, 240, 240));

        JPasswordField txtCurrent = new JPasswordField(20);
        JPasswordField txtPass    = new JPasswordField(20);
        JPasswordField txtConfirm = new JPasswordField(20);

        String[] labels    = {"Full Name (read-only)", "Username (read-only)",
                "Current Password", "New Password", "Confirm Password"};
        Component[] inputs = {txtName, txtUser, txtCurrent, txtPass, txtConfirm};

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
            String current = new String(txtCurrent.getPassword());
            String pass    = new String(txtPass.getPassword());
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

            if (!authService.verifyTrainerPassword(trainer.getTrainerId(), current)) {
                JOptionPane.showMessageDialog(panel,
                        "Current password is incorrect.",
                        "Authentication Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }

            AuthService.ResetResult result =
                    authService.resetTrainerPasswordByUsername(trainer.getUsername(), pass);
            if (result == AuthService.ResetResult.SUCCESS) {
                JOptionPane.showMessageDialog(panel, "Password updated successfully.");
                txtCurrent.setText("");
                txtPass.setText("");
                txtConfirm.setText("");
            } else {
                JOptionPane.showMessageDialog(panel,
                        "Could not update password. Please try again.",
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    // --- My Schedule Tab ---

    private JPanel buildSchedulePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        String[] wdCols = {"Day", "Working Start", "Working End"};
        DefaultTableModel wdModel = new DefaultTableModel(wdCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (TrainerWorkingDay wd : ptService.getWorkingDays(trainer.getTrainerId())) {
            wdModel.addRow(new Object[]{
                    wd.getDayOfWeek(), wd.getStartTime(), wd.getEndTime()
            });
        }

        String[] slotCols = {"Day", "Lesson Start", "Lesson End"};
        DefaultTableModel slotModel = new DefaultTableModel(slotCols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (TrainerLessonSlot s : ptService.getLessonSlots(trainer.getTrainerId())) {
            slotModel.addRow(new Object[]{
                    s.getDayOfWeek(), s.getStartTime(), s.getEndTime()
            });
        }

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