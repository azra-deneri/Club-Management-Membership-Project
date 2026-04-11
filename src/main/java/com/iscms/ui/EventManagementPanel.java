package com.iscms.ui;

import com.iscms.model.Event;
import com.iscms.model.EventRegistration;
import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.service.EventFactory;
import com.iscms.service.EventService;
import com.iscms.service.MemberService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Panel for event management — available in Manager Dashboard Events tab
// Supports: create, edit, cancel, capacity increase, and view registrations
public class EventManagementPanel extends JPanel {

    // The currently logged-in manager
    private final Manager manager;

    // Service instances — no DAOs directly in UI layer
    private final EventService eventService   = new EventService();
    private final MemberService memberService = new MemberService();

    // Table and model for the event list
    private JTable eventTable;
    private DefaultTableModel tableModel;

    public EventManagementPanel(Manager manager) {
        this.manager = manager;
        setLayout(new BorderLayout());
        initUI();
        loadEvents();
    }

    // Builds the panel layout: toolbar + event table
    private void initUI() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh  = new JButton("Refresh");
        JButton btnCreate   = new JButton("Create Event");
        JButton btnCancel   = new JButton("Cancel Event");
        JButton btnCapacity = new JButton("Edit Capacity");
        JButton btnDetail   = new JButton("View Registrations");
        JButton btnEdit     = new JButton("Edit Event");

        btnEdit.setBackground(new Color(180, 130, 0));
        btnEdit.setForeground(Color.WHITE);
        btnEdit.setOpaque(true);
        btnEdit.setBorderPainted(false);
        toolbar.add(btnEdit);
        btnEdit.addActionListener(e -> showEditDialog());

        btnCreate.setBackground(new Color(33, 87, 141));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.setOpaque(true);
        btnCreate.setBorderPainted(false);

        btnCancel.setBackground(new Color(150, 50, 50));
        btnCancel.setForeground(Color.WHITE);
        btnCancel.setOpaque(true);
        btnCancel.setBorderPainted(false);

        btnCapacity.setBackground(new Color(180, 130, 0));
        btnCapacity.setForeground(Color.WHITE);
        btnCapacity.setOpaque(true);
        btnCapacity.setBorderPainted(false);

        toolbar.add(btnRefresh);
        toolbar.add(btnCreate);
        toolbar.add(btnCancel);
        toolbar.add(btnCapacity);
        toolbar.add(btnDetail);
        add(toolbar, BorderLayout.NORTH);

        // Non-editable event table showing all events
        String[] cols = {"ID", "Name", "Category", "Date", "Start", "End",
                "Location", "Capacity", "Registered", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        eventTable = new JTable(tableModel);
        eventTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        eventTable.getTableHeader().setReorderingAllowed(false);
        add(new JScrollPane(eventTable), BorderLayout.CENTER);

        btnRefresh.addActionListener(e  -> loadEvents());
        btnCreate.addActionListener(e   -> showCreateDialog());
        btnCancel.addActionListener(e   -> cancelSelectedEvent());
        btnCapacity.addActionListener(e -> showEditCapacityDialog());
        btnDetail.addActionListener(e   -> showRegistrations());
    }

    // Loads all events (ACTIVE, CANCELLED, EXPIRED) into the table
    // Also fetches the current registered count per event
    private void loadEvents() {
        tableModel.setRowCount(0);
        for (Event e : eventService.getAllEvents()) {
            int registered = eventService.countRegistered(e.getEventId());
            tableModel.addRow(new Object[]{
                    e.getEventId(), e.getEventName(), e.getCategory(),
                    e.getEventDate(), e.getStartTime(), e.getEndTime(),
                    e.getLocation(), e.getCapacity(), registered, e.getStatus()
            });
        }
    }

    // Opens a dialog for creating a new event
    // Uses EventFactory to construct the event object with validation
    private void showCreateDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Create Event", true);
        dialog.setSize(460, 460);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField txtName      = new JTextField(20);
        JComboBox<String> cbCat = new JComboBox<>(new String[]{
                "FITNESS", "YOGA", "SWIMMING", "HIIT", "WORKSHOP", "OTHER"});
        JTextField txtDate      = new JTextField("YYYY-MM-DD");
        JTextField txtStart     = new JTextField("HH:MM");
        JTextField txtEnd       = new JTextField("HH:MM");
        JTextField txtLocation  = new JTextField(20);
        JTextField txtCapacity  = new JTextField("50");
        JTextArea  txtDesc      = new JTextArea(3, 20);

        String[] labels    = {"Event Name *", "Category *", "Date * (YYYY-MM-DD)",
                "Start Time * (HH:MM)", "End Time * (HH:MM)",
                "Location", "Capacity *", "Description"};
        Component[] inputs = {txtName, cbCat, txtDate, txtStart, txtEnd,
                txtLocation, txtCapacity, new JScrollPane(txtDesc)};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.3;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.7;
            panel.add(inputs[i], c);
        }

        JButton btnSave = new JButton("Create");
        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        dialog.add(new JScrollPane(panel));

        btnSave.addActionListener(e -> {
            try {
                // Parse input fields — exceptions caught and shown to user
                LocalDate date  = LocalDate.parse(txtDate.getText().trim());
                LocalTime start = LocalTime.parse(txtStart.getText().trim());
                LocalTime end   = LocalTime.parse(txtEnd.getText().trim());
                int capacity    = Integer.parseInt(txtCapacity.getText().trim());

                // EventFactory calls EventBuilder internally — validation runs in build()
                Event event = EventFactory.createEvent(
                        txtName.getText().trim(),
                        (String) cbCat.getSelectedItem(),
                        date, start, end,
                        txtLocation.getText().trim(),
                        capacity,
                        txtDesc.getText().trim(),
                        manager.getManagerId());

                // Business rules (future date, capacity > 0) enforced in EventService
                eventService.createEvent(event);
                JOptionPane.showMessageDialog(dialog, "Event created successfully!");
                dialog.dispose();
                loadEvents();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.setVisible(true);
    }

    // Opens a dialog for increasing an event's capacity
    // UI-level guard prevents opening for cancelled events
    // Actual business rules (5-hour window, must be greater) enforced in EventService
    private void showEditCapacityDialog() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an event first.");
            return;
        }
        int eventId         = (int) tableModel.getValueAt(row, 0);
        String name         = (String) tableModel.getValueAt(row, 1);
        int currentCapacity = (int) tableModel.getValueAt(row, 7);
        String status       = (String) tableModel.getValueAt(row, 9);

        // UI-level early exit — real validation still happens in service
        if ("CANCELLED".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    "Cannot edit a cancelled event.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String input = JOptionPane.showInputDialog(this,
                "Event: " + name + "\nCurrent Capacity: " + currentCapacity
                        + "\n\nEnter new capacity (must be greater than current):",
                "Edit Capacity", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.isBlank()) return;

        try {
            int newCapacity = Integer.parseInt(input.trim());
            // Business rules enforced in EventService.increaseCapacity()
            eventService.increaseCapacity(eventId, newCapacity);
            JOptionPane.showMessageDialog(this,
                    "Capacity updated to " + newCapacity + " successfully.");
            loadEvents();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Cancels the selected event after confirmation
    // Also cancels all member registrations for that event via EventService
    private void cancelSelectedEvent() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an event first.");
            return;
        }
        int eventId = (int) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel event '" + name + "'?\nAll registrations will be cancelled.",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            eventService.cancelEvent(eventId);
            loadEvents();
        }
    }

    // Shows all registrations for the selected event in a popup table
    // Resolves member names from IDs via MemberService
    private void showRegistrations() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an event first.");
            return;
        }
        int eventId = (int) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        List<EventRegistration> regs = eventService.getRegistrationsByEvent(eventId);

        String[] cols = {"Reg ID", "Member Name", "Date", "Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        for (EventRegistration r : regs) {
            // Resolve member name from ID — fallback to ID if member not found
            String memberName = memberService.getMemberById(r.getMemberId())
                    .map(Member::getFullName)
                    .orElse("ID: " + r.getMemberId());
            model.addRow(new Object[]{
                    r.getRegistrationId(),
                    memberName,
                    r.getRegistrationDate() != null
                            ? r.getRegistrationDate().toLocalDate() : "-",
                    r.getStatus()
            });
        }

        JTable table = new JTable(model);
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Registrations — " + name, true);
        dialog.setSize(550, 380);
        dialog.setLocationRelativeTo(this);
        dialog.add(new JScrollPane(table));
        dialog.setVisible(true);
    }

    // Opens a dialog for editing an existing event's details
    // Pre-fills all fields with current event data
    // UI-level guard prevents opening for cancelled events
    private void showEditDialog() {
        int row = eventTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an event first.");
            return;
        }

        int eventId   = (int) tableModel.getValueAt(row, 0);
        String status = (String) tableModel.getValueAt(row, 9);

        // UI-level early exit — real validation still happens in service
        if ("CANCELLED".equals(status)) {
            JOptionPane.showMessageDialog(this,
                    "Cannot edit a cancelled event.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Event event = eventService.getEventById(eventId).orElse(null);
        if (event == null) return;

        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
                "Edit Event", true);
        dialog.setSize(460, 420);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Pre-fill all fields with existing event data
        JTextField txtName      = new JTextField(event.getEventName(), 20);
        JComboBox<String> cbCat = new JComboBox<>(new String[]{
                "FITNESS", "YOGA", "SWIMMING", "HIIT", "WORKSHOP", "OTHER"});
        cbCat.setSelectedItem(event.getCategory());
        JTextField txtDate      = new JTextField(event.getEventDate().toString(), 20);
        JTextField txtStart     = new JTextField(event.getStartTime().toString(), 20);
        JTextField txtEnd       = new JTextField(event.getEndTime().toString(), 20);
        JTextField txtLocation  = new JTextField(
                event.getLocation() != null ? event.getLocation() : "", 20);
        JTextArea txtDesc       = new JTextArea(
                event.getDescription() != null ? event.getDescription() : "", 3, 20);

        String[] labels    = {"Event Name *", "Category *", "Date * (YYYY-MM-DD)",
                "Start Time * (HH:MM)", "End Time * (HH:MM)",
                "Location", "Description"};
        Component[] inputs = {txtName, cbCat, txtDate, txtStart, txtEnd,
                txtLocation, new JScrollPane(txtDesc)};

        for (int i = 0; i < inputs.length; i++) {
            c.gridx = 0; c.gridy = i; c.weightx = 0.3;
            panel.add(new JLabel(labels[i] + ":"), c);
            c.gridx = 1; c.weightx = 0.7;
            panel.add(inputs[i], c);
        }

        JButton btnSave = new JButton("Save Changes");
        btnSave.setBackground(new Color(33, 87, 141));
        btnSave.setForeground(Color.WHITE);
        btnSave.setOpaque(true);
        btnSave.setBorderPainted(false);
        c.gridx = 0; c.gridy = inputs.length; c.gridwidth = 2;
        panel.add(btnSave, c);

        dialog.add(new JScrollPane(panel));

        btnSave.addActionListener(e -> {
            try {
                // Business rules (future date, end after start) enforced in EventService
                eventService.updateEvent(
                        eventId,
                        txtName.getText().trim(),
                        (String) cbCat.getSelectedItem(),
                        LocalDate.parse(txtDate.getText().trim()),
                        LocalTime.parse(txtStart.getText().trim()),
                        LocalTime.parse(txtEnd.getText().trim()),
                        txtLocation.getText().trim(),
                        txtDesc.getText().trim());
                JOptionPane.showMessageDialog(dialog, "Event updated successfully.");
                dialog.dispose();
                loadEvents();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.setVisible(true);
    }
}