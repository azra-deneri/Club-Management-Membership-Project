package com.iscms.ui;

import com.iscms.model.Event;
import com.iscms.model.EventRegistration;
import com.iscms.model.Member;
import com.iscms.service.EventService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

// Panel for members to browse available events and manage their own registrations
// Shown as a tab inside MemberDashboard
public class MemberEventPanel extends JPanel {

    // The currently logged-in member
    private final Member member;

    // Service instance — no DAOs directly in UI layer
    private final EventService eventService = new EventService();

    // Tables and models for both sub-tabs
    private JTable eventTable;
    private DefaultTableModel tableModel;
    private DefaultTableModel myRegModel;

    public MemberEventPanel(Member member) {
        this.member = member;
        setLayout(new BorderLayout());
        // Two sub-tabs: one for browsing events, one for managing own registrations
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Available Events", buildAvailablePanel());
        tabs.addTab("My Registrations", buildMyRegistrationsPanel());
        add(tabs, BorderLayout.CENTER);
    }

    // --- Available Events Tab ---

    // Builds the available events panel with register action
    private JPanel buildAvailablePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh  = new JButton("Refresh");
        JButton btnRegister = new JButton("Register");
        btnRegister.setBackground(new Color(33, 120, 80));
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setOpaque(true);
        btnRegister.setBorderPainted(false);
        toolbar.add(btnRefresh);
        toolbar.add(btnRegister);
        panel.add(toolbar, BorderLayout.NORTH);

        // Non-editable event table showing available events
        String[] cols = {"ID", "Name", "Category", "Date", "Start",
                "Location", "Spots Left", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        eventTable = new JTable(tableModel);
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(eventTable), BorderLayout.CENTER);

        btnRefresh.addActionListener(e  -> loadEvents());
        btnRegister.addActionListener(e -> registerToEvent());
        loadEvents();
        return panel;
    }

    // Loads only ACTIVE events and calculates remaining spots for each
    // Shows "FULL" instead of 0 for better user readability
    private void loadEvents() {
        tableModel.setRowCount(0);
        for (Event e : eventService.getActiveEvents()) {
            int registered = eventService.countRegistered(e.getEventId());
            int spotsLeft  = e.getCapacity() - registered;
            tableModel.addRow(new Object[]{
                    e.getEventId(), e.getEventName(), e.getCategory(),
                    e.getEventDate(), e.getStartTime(), e.getLocation(),
                    spotsLeft > 0 ? spotsLeft : "FULL",
                    e.getStatus()
            });
        }
    }

    // Registers the member for the selected event
    // All business rules (active status, quota, duplicate, past event) enforced in service
    private void registerToEvent() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an event first.");
            return;
        }
        int eventId = (int) tableModel.getValueAt(row, 0);
        try {
            eventService.registerMember(member.getMemberId(), eventId, member.getStatus());
            JOptionPane.showMessageDialog(this, "Successfully registered for the event!");
            // Refresh both tabs to reflect the new registration
            loadEvents();
            refreshMyRegistrations();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // --- My Registrations Tab ---

    // Builds the member's registration history panel with cancel action
    private JPanel buildMyRegistrationsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh = new JButton("Refresh");
        JButton btnCancel  = new JButton("Cancel Registration");
        btnCancel.setBackground(new Color(150, 50, 50));
        btnCancel.setForeground(Color.WHITE);
        btnCancel.setOpaque(true);
        btnCancel.setBorderPainted(false);
        toolbar.add(btnRefresh);
        toolbar.add(btnCancel);
        panel.add(toolbar, BorderLayout.NORTH);

        // Non-editable registration history table
        String[] cols = {"Reg ID", "Event Name", "Date", "Category", "Status"};
        myRegModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable myRegTable = new JTable(myRegModel);
        myRegTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(myRegTable), BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> refreshMyRegistrations());

        // Cancels the selected registration
        // Finds the eventId from the registration record, then delegates to service
        // Business rules (24-hour cancellation window) enforced in service
        btnCancel.addActionListener(e -> {
            int row = myRegTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Please select a registration first.");
                return;
            }
            int regId = (int) myRegModel.getValueAt(row, 0);
            // Look up the full registration object to get the event ID
            List<EventRegistration> regs =
                    eventService.getRegistrationsByMember(member.getMemberId());
            regs.stream()
                    .filter(r -> r.getRegistrationId() == regId)
                    .findFirst()
                    .ifPresent(r -> {
                        try {
                            eventService.cancelRegistration(
                                    member.getMemberId(), r.getEventId());
                            JOptionPane.showMessageDialog(this, "Registration cancelled.");
                            // Refresh both tabs to reflect the cancellation
                            refreshMyRegistrations();
                            loadEvents();
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, ex.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
        });

        refreshMyRegistrations();
        return panel;
    }

    // Reloads the member's registration list with event details resolved from event ID
    // Joins registration records with event data for display
    private void refreshMyRegistrations() {
        myRegModel.setRowCount(0);
        for (EventRegistration r :
                eventService.getRegistrationsByMember(member.getMemberId())) {
            // Resolve event details from event ID for display
            eventService.getEventById(r.getEventId()).ifPresent(e ->
                    myRegModel.addRow(new Object[]{
                            r.getRegistrationId(), e.getEventName(),
                            e.getEventDate(), e.getCategory(), r.getStatus()
                    })
            );
        }
    }
}