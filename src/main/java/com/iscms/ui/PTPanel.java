package com.iscms.ui;

import com.iscms.model.*;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

// Panel for members to book PT appointments and view their appointment history
// Shown as a tab inside MemberDashboard
public class PTPanel extends JPanel {

    // The currently logged-in member
    private final Member member;

    // Service instances — no DAOs directly in UI layer
    private final PTService ptService         = new PTService();
    private final MemberService memberService = new MemberService();

    // State for week navigation and slot selection
    private LocalDate currentWeekStart;
    private JComboBox<String> cbTrainer;
    private List<Trainer> trainers;
    private JLabel lblWeek;
    private JPanel slotsPanel;
    private JLabel lblTierInfo;
    private DefaultTableModel aptModel;

    public PTPanel(Member member) {
        this.member = member;
        // Initialize to current week's Monday
        this.currentWeekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Book Appointment", buildBookPanel());
        tabs.addTab("My Appointments",  buildMyAppointmentsPanel());
        add(tabs, BorderLayout.CENTER);
    }

    // --- Book Appointment Tab ---

    // Builds the slot booking panel with trainer selector, week navigation, and slot cards
    private JPanel buildBookPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        // Load only active trainers — inactive trainers cannot accept bookings
        trainers = ptService.getActiveTrainers();

        // Determine member tier to show session limit info
        Optional<Membership> ms = memberService.getActiveMembership(member.getMemberId());
        String tier = ms.map(Membership::getTier).orElse("CLASSIC");
        String tierInfo = switch (tier) {
            case "GOLD" -> "GOLD: max 2 sessions/month";
            case "VIP"  -> "VIP: max 4 sessions/month";
            default     -> "CLASSIC: PT not available";
        };

        // Top bar — trainer dropdown and tier limit label
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        topBar.add(new JLabel("Trainer:"));
        cbTrainer = new JComboBox<>();
        for (Trainer t : trainers)
            cbTrainer.addItem(t.getTrainerId() + " — " + t.getFullName()
                    + (t.getSpecialty() != null ? " (" + t.getSpecialty() + ")" : ""));
        topBar.add(cbTrainer);

        lblTierInfo = new JLabel(tierInfo);
        lblTierInfo.setForeground(new Color(33, 87, 141));
        lblTierInfo.setFont(new Font("Arial", Font.BOLD, 12));
        topBar.add(lblTierInfo);
        panel.add(topBar, BorderLayout.NORTH);

        // Week navigation bar with prev/next week buttons and current week label
        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 4));
        JButton btnPrev = new JButton("◄ Prev Week");
        JButton btnNext = new JButton("Next Week ►");
        lblWeek = new JLabel("", SwingConstants.CENTER);
        lblWeek.setFont(new Font("Arial", Font.BOLD, 13));
        navBar.add(btnPrev);
        navBar.add(lblWeek);
        navBar.add(btnNext);

        // Slots panel — dynamically populated by loadSlots()
        slotsPanel = new JPanel();
        slotsPanel.setLayout(new BoxLayout(slotsPanel, BoxLayout.Y_AXIS));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 4));
        centerPanel.add(navBar, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(slotsPanel), BorderLayout.CENTER);
        panel.add(centerPanel, BorderLayout.CENTER);

        // Book button — triggers booking for the currently selected slot
        JButton btnBook = new JButton("Book Selected Slot");
        btnBook.setBackground(new Color(33, 120, 80));
        btnBook.setForeground(Color.WHITE);
        btnBook.setOpaque(true);
        btnBook.setBorderPainted(false);
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomBar.add(btnBook);
        panel.add(bottomBar, BorderLayout.SOUTH);

        // Array-wrapped references to allow mutation inside lambda
        final TrainerLessonSlot[] selectedSlot = {null};
        final LocalDate[]         selectedDate  = {null};

        loadSlots(selectedSlot, selectedDate);

        // Reload slots when trainer changes or week navigation is used
        cbTrainer.addActionListener(e -> loadSlots(selectedSlot, selectedDate));
        btnPrev.addActionListener(e -> {
            currentWeekStart = currentWeekStart.minusWeeks(1);
            loadSlots(selectedSlot, selectedDate);
        });
        btnNext.addActionListener(e -> {
            currentWeekStart = currentWeekStart.plusWeeks(1);
            loadSlots(selectedSlot, selectedDate);
        });

        btnBook.addActionListener(e -> {
            if (selectedSlot[0] == null || selectedDate[0] == null) {
                JOptionPane.showMessageDialog(panel, "Please select a slot first.");
                return;
            }
            int idx = cbTrainer.getSelectedIndex();
            if (idx < 0) return;
            Trainer t = trainers.get(idx);
            try {
                // All business rules enforced in PTService.bookAppointment:
                // tier eligibility, monthly session limit, no-show penalty, slot conflict
                ptService.bookAppointment(member.getMemberId(), t.getTrainerId(),
                        selectedDate[0], selectedSlot[0].getStartTime(), selectedSlot[0].getEndTime());
                JOptionPane.showMessageDialog(panel, "Appointment booked successfully!");
                // Clear selection and refresh slots
                selectedSlot[0] = null;
                selectedDate[0] = null;
                loadSlots(selectedSlot, selectedDate);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return panel;
    }

    // Renders lesson slots for the selected trainer and current week
    // Each slot is shown as a color-coded card:
    // Green = AVAILABLE, Red = UNAVAILABLE/past, Blue = BOOKED by this member, Dark green = SELECTED
    private void loadSlots(TrainerLessonSlot[] selectedSlot, LocalDate[] selectedDate) {
        slotsPanel.removeAll();
        lblWeek.setText(currentWeekStart + "  —  " + currentWeekStart.plusDays(6));

        int idx = cbTrainer.getSelectedIndex();
        if (idx < 0 || trainers.isEmpty()) {
            slotsPanel.revalidate();
            slotsPanel.repaint();
            return;
        }

        Trainer trainer = trainers.get(idx);
        List<TrainerWorkingDay>  workingDays = ptService.getWorkingDays(trainer.getTrainerId());
        List<TrainerLessonSlot>  slots       = ptService.getLessonSlots(trainer.getTrainerId());

        // Load member's existing appointments for conflict highlighting
        List<PersonalTrainingAppointment> myApts =
                ptService.getMemberAppointments(member.getMemberId());

        if (workingDays.isEmpty()) {
            slotsPanel.add(new JLabel("  No working days defined for this trainer."));
            slotsPanel.revalidate();
            slotsPanel.repaint();
            return;
        }

        for (TrainerWorkingDay wd : workingDays) {
            DayOfWeek dow  = DayOfWeek.valueOf(wd.getDayOfWeek());
            LocalDate date = currentWeekStart.with(TemporalAdjusters.nextOrSame(dow));

            // Day header row showing day name, date, and working hours
            JPanel dayHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            dayHeader.setBackground(new Color(220, 230, 245));
            dayHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            JLabel lblDay = new JLabel("\uD83D\uDCC5  " + wd.getDayOfWeek() + " — " + date
                    + "  (" + wd.getStartTime() + " - " + wd.getEndTime() + ")");
            lblDay.setFont(new Font("Arial", Font.BOLD, 12));
            dayHeader.add(lblDay);
            slotsPanel.add(dayHeader);

            // Filter slots for this specific day of the week
            List<TrainerLessonSlot> daySlots = slots.stream()
                    .filter(s -> s.getDayOfWeek().equals(wd.getDayOfWeek()))
                    .toList();

            if (daySlots.isEmpty()) {
                JLabel lblEmpty = new JLabel("      No lesson slots defined for this day.");
                lblEmpty.setFont(new Font("Arial", Font.ITALIC, 11));
                lblEmpty.setForeground(Color.GRAY);
                lblEmpty.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 0));
                slotsPanel.add(lblEmpty);
            } else {
                JPanel daySlotRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
                for (TrainerLessonSlot slot : daySlots) {

                    // Check availability from service — avoids showing stale data
                    boolean trainerSlotTaken = ptService.isSlotTaken(trainer.getTrainerId(),
                            date, slot.getStartTime(), slot.getEndTime());
                    boolean isPast = date.isBefore(LocalDate.now());

                    // Check if this member has already booked this exact slot
                    boolean bookedByMe = myApts.stream().anyMatch(a ->
                            a.getTrainerId() == trainer.getTrainerId()
                                    && a.getAppointmentDate().equals(date)
                                    && a.getStartTime().equals(slot.getStartTime())
                                    && a.getEndTime().equals(slot.getEndTime())
                                    && "SCHEDULED".equals(a.getStatus()));

                    // Slot card — fixed size with time and availability label
                    JPanel card = new JPanel();
                    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
                    card.setPreferredSize(new Dimension(130, 60));
                    card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

                    JLabel lblTime   = new JLabel(slot.getStartTime() + " — " + slot.getEndTime(),
                            SwingConstants.CENTER);
                    JLabel lblStatus;

                    // Color-code card based on slot state
                    if (bookedByMe) {
                        // Blue — member has already booked this slot
                        lblStatus = new JLabel("✓ BOOKED", SwingConstants.CENTER);
                        card.setBackground(new Color(33, 87, 141));
                        lblTime.setForeground(Color.WHITE);
                        lblStatus.setForeground(Color.WHITE);
                    } else if (trainerSlotTaken || isPast) {
                        // Red — trainer busy or date is in the past
                        lblStatus = new JLabel("✗ UNAVAILABLE", SwingConstants.CENTER);
                        card.setBackground(new Color(220, 80, 80));
                        lblTime.setForeground(Color.WHITE);
                        lblStatus.setForeground(Color.WHITE);
                    } else {
                        // Green — slot is open and bookable
                        lblStatus = new JLabel("✓ AVAILABLE", SwingConstants.CENTER);
                        card.setBackground(new Color(144, 238, 144));
                        lblTime.setForeground(new Color(0, 80, 0));
                        lblStatus.setForeground(new Color(0, 80, 0));
                    }

                    lblTime.setAlignmentX(CENTER_ALIGNMENT);
                    lblStatus.setAlignmentX(CENTER_ALIGNMENT);
                    lblTime.setFont(new Font("Arial", Font.BOLD, 11));
                    lblStatus.setFont(new Font("Arial", Font.PLAIN, 10));

                    card.add(Box.createVerticalGlue());
                    card.add(lblTime);
                    card.add(lblStatus);
                    card.add(Box.createVerticalGlue());
                    card.setOpaque(true);

                    // Only attach click listener to available slots
                    if (!trainerSlotTaken && !isPast && !bookedByMe) {
                        LocalDate finalDate = date;
                        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        card.addMouseListener(new java.awt.event.MouseAdapter() {
                            public void mouseClicked(java.awt.event.MouseEvent e) {
                                selectedSlot[0] = slot;
                                selectedDate[0] = finalDate;
                                // Reload to update selection highlight
                                loadSlots(selectedSlot, selectedDate);
                            }
                        });
                    }

                    // Highlight currently selected slot in dark green
                    if (selectedSlot[0] != null && selectedDate[0] != null
                            && selectedDate[0].equals(date)
                            && selectedSlot[0].getStartTime().equals(slot.getStartTime())
                            && !bookedByMe && !trainerSlotTaken) {
                        card.setBackground(new Color(33, 120, 80));
                        lblTime.setForeground(Color.WHITE);
                        lblStatus.setForeground(Color.WHITE);
                        lblStatus.setText("► SELECTED");
                    }

                    daySlotRow.add(card);
                }
                slotsPanel.add(daySlotRow);
            }
        }

        slotsPanel.revalidate();
        slotsPanel.repaint();
    }

    // --- My Appointments Tab ---

    // Builds the appointment history panel with cancel action
    private JPanel buildMyAppointmentsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnRefresh = new JButton("Refresh");
        JButton btnCancel  = new JButton("Cancel Appointment");
        btnCancel.setBackground(new Color(150, 50, 50));
        btnCancel.setForeground(Color.WHITE);
        btnCancel.setOpaque(true);
        btnCancel.setBorderPainted(false);
        toolbar.add(btnRefresh);
        toolbar.add(btnCancel);
        panel.add(toolbar, BorderLayout.NORTH);

        String[] cols = {"ID", "Trainer", "Date", "Start", "End", "Status"};
        aptModel = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(aptModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        btnRefresh.addActionListener(e -> loadMyAppointments());

        // Cancels the selected appointment
        // Business rule (cannot cancel past appointments) enforced in service
        btnCancel.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(panel, "Please select an appointment.");
                return;
            }
            int aptId = (int) aptModel.getValueAt(row, 0);
            try {
                ptService.cancelAppointment(aptId);
                JOptionPane.showMessageDialog(panel, "Appointment cancelled.");
                loadMyAppointments();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        loadMyAppointments();
        return panel;
    }

    // Loads the member's appointment history
    // Uses getAllTrainers() (not just active) to resolve trainer names for historical records
    private void loadMyAppointments() {
        aptModel.setRowCount(0);
        // Use all trainers including inactive — historical appointments may reference deactivated trainers
        List<Trainer> allTrainers = ptService.getAllTrainers();
        for (PersonalTrainingAppointment apt : ptService.getMemberAppointments(member.getMemberId())) {
            // Resolve trainer name from ID — fallback to ID if trainer not found
            String trainerName = allTrainers.stream()
                    .filter(t -> t.getTrainerId() == apt.getTrainerId())
                    .map(Trainer::getFullName)
                    .findFirst().orElse("ID: " + apt.getTrainerId());
            aptModel.addRow(new Object[]{
                    apt.getAppointmentId(), trainerName,
                    apt.getAppointmentDate(), apt.getStartTime(),
                    apt.getEndTime(), apt.getStatus()
            });
        }
    }
}