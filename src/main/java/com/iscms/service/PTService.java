package com.iscms.service;

import com.iscms.dao.*;
import com.iscms.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// Service class responsible for all personal training operations
// Handles appointment booking, cancellation, trainer management, working days and lesson slots
public class PTService {

    private final AppointmentDAO appointmentDAO;
    private final TrainerDAO trainerDAO;
    private final MemberDAO memberDAO;
    private final MembershipDAO membershipDAO;

    // Grace period after appointment end before trainer can mark as NO_SHOW
    // Gives the member a reasonable window to arrive late without being penalized
    private static final long NO_SHOW_GRACE_HOURS = 1;

    // Default constructor — creates concrete DAO implementations
    public PTService() {
        this.appointmentDAO = new AppointmentDAOImpl();
        this.trainerDAO     = new TrainerDAOImpl();
        this.memberDAO      = new MemberDAOImpl();
        this.membershipDAO  = new MembershipDAOImpl();
    }

    // Constructor for unit testing — allows injecting mock DAO objects
    public PTService(AppointmentDAO appointmentDAO, TrainerDAO trainerDAO,
                     MemberDAO memberDAO, MembershipDAO membershipDAO) {
        this.appointmentDAO = appointmentDAO;
        this.trainerDAO     = trainerDAO;
        this.memberDAO      = memberDAO;
        this.membershipDAO  = membershipDAO;
    }

    // Books a PT appointment for a member with a trainer
    // Enforces all PT business rules in order:
    // 0. Time validity: end time must be strictly after start time
    // 1. Member must have an active membership
    // 2. CLASSIC tier cannot book PT sessions
    // 3. Monthly session limit: GOLD = 2, VIP = 4
    // 4. Member must not have an active no-show penalty
    // 5. Trainer slot must not already be taken
    // 6. Member must not have a conflicting appointment at the same time
    public void bookAppointment(int memberId, int trainerId,
                                LocalDate date, LocalTime start, LocalTime end) {

        // Check 0: end time must be strictly after start time
        if (start == null || end == null)
            throw new IllegalArgumentException("Start and end time are required.");
        if (!end.isAfter(start))
            throw new IllegalArgumentException(
                    "End time (" + end + ") must be after start time (" + start + ").");

        // Check 1: active membership required
        Membership ms = membershipDAO.findActiveByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("No active membership."));

        // Check 2: CLASSIC members cannot book PT sessions
        if ("CLASSIC".equals(ms.getTier()))
            throw new IllegalStateException("Classic members cannot book PT sessions.");

        // Check 3: monthly session limit based on tier
        int monthLimit = "GOLD".equals(ms.getTier()) ? 2 : 4;
        int used = appointmentDAO.countMonthlyAppointments(
                memberId, date.getYear(), date.getMonthValue());
        if (used >= monthLimit)
            throw new IllegalStateException(
                    "Monthly PT session limit reached (" + monthLimit + ").");

        // Check 4: no-show penalty blocks new bookings for 7 days
        Optional<LocalDate> penalty = appointmentDAO.findActiveNoShowPenalty(memberId);
        if (penalty.isPresent())
            throw new IllegalStateException(
                    "You have a no-show penalty until " + penalty.get() + ".");

        // Check 5: trainer slot must not be taken by another member
        if (appointmentDAO.isSlotTaken(trainerId, date, start, end))
            throw new IllegalStateException("This trainer slot is already taken.");

        // Check 6: member must not have an overlapping appointment
        if (appointmentDAO.hasMemberConflict(memberId, date, start, end))
            throw new IllegalStateException("You already have an appointment at this time.");

        // All checks passed — create and insert the appointment
        PersonalTrainingAppointment apt = new PersonalTrainingAppointment();
        apt.setMemberId(memberId);
        apt.setTrainerId(trainerId);
        apt.setAppointmentDate(date);
        apt.setStartTime(start);
        apt.setEndTime(end);
        apt.setStatus("SCHEDULED");
        appointmentDAO.insert(apt);
    }

    // Cancels a PT appointment.
    // Two preconditions:
    //   - Status must be SCHEDULED — already-cancelled, completed, or no-show
    //     appointments cannot be re-cancelled.
    //   - Appointment must not have started yet — once the session begins,
    //     it's too late to cancel.
    public void cancelAppointment(int appointmentId) {
        PersonalTrainingAppointment apt = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        // Status guard — only SCHEDULED appointments can transition to CANCELLED
        if (!"SCHEDULED".equals(apt.getStatus()))
            throw new IllegalStateException(
                    "Cannot cancel an appointment that is already " + apt.getStatus() + ".");

        // Time guard — block cancellation if the appointment has already started
        LocalDateTime startsAt = LocalDateTime.of(apt.getAppointmentDate(), apt.getStartTime());
        if (!LocalDateTime.now().isBefore(startsAt))
            throw new IllegalStateException(
                    "This appointment has already started or passed and cannot be cancelled.");

        appointmentDAO.updateStatus(appointmentId, "CANCELLED");
    }

    // Marks an appointment as COMPLETED — called by trainer after the session.
    // Two preconditions:
    //   - Status must be SCHEDULED — cancelled / already-completed / no-show
    //     appointments cannot be re-marked.
    //   - Appointment must have started — a session that hasn't begun yet
    //     has nothing to complete.
    public void markCompleted(int appointmentId) {
        PersonalTrainingAppointment apt = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        // Status guard
        if (!"SCHEDULED".equals(apt.getStatus()))
            throw new IllegalStateException(
                    "Cannot mark this appointment as completed — it is " + apt.getStatus() + ".");

        // Time guard — block if appointment hasn't started yet
        LocalDateTime startsAt = LocalDateTime.of(apt.getAppointmentDate(), apt.getStartTime());
        if (LocalDateTime.now().isBefore(startsAt))
            throw new IllegalStateException(
                    "Cannot mark a future appointment as completed. "
                            + "The session must have started first.");

        appointmentDAO.updateStatus(appointmentId, "COMPLETED");
    }

    // Marks an appointment as NO_SHOW and applies a 7-day booking penalty.
    // The penalty blocks the member from booking new PT sessions for 7 days.
    //
    // Two preconditions:
    //   - Status must be SCHEDULED — cancelled / completed / already-no-show
    //     appointments cannot be re-marked.
    //   - At least NO_SHOW_GRACE_HOURS (1 hour) must have passed since the
    //     appointment's end time. This prevents trainers from penalizing members
    //     for future sessions, and gives members a reasonable late-arrival window.
    public void markNoShow(int appointmentId) {
        PersonalTrainingAppointment apt = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        // Status guard
        if (!"SCHEDULED".equals(apt.getStatus()))
            throw new IllegalStateException(
                    "Cannot mark this appointment as no-show — it is " + apt.getStatus() + ".");

        // Time guard — must wait until grace period after appointment end has passed
        LocalDateTime canMarkAfter = LocalDateTime
                .of(apt.getAppointmentDate(), apt.getEndTime())
                .plusHours(NO_SHOW_GRACE_HOURS);

        if (LocalDateTime.now().isBefore(canMarkAfter))
            throw new IllegalStateException(
                    "Cannot mark no-show yet. You can mark this appointment as no-show "
                            + "after " + canMarkAfter + " (1 hour after the session ends).");

        appointmentDAO.updateStatus(appointmentId, "NO_SHOW");
        appointmentDAO.updateNoShowPenalty(appointmentId, LocalDate.now().plusDays(7));
    }

    // Returns all appointments for a given member
    public List<PersonalTrainingAppointment> getMemberAppointments(int memberId) {
        return appointmentDAO.findByMemberId(memberId);
    }

    // Returns all appointments assigned to a given trainer
    public List<PersonalTrainingAppointment> getTrainerAppointments(int trainerId) {
        return appointmentDAO.findByTrainerId(trainerId);
    }

    // Returns only active trainers — used for member PT booking
    public List<Trainer> getActiveTrainers() {
        return trainerDAO.findActive();
    }

    // Returns all trainers including inactive — used for manager view and history
    public List<Trainer> getAllTrainers() {
        return trainerDAO.findAll();
    }

    // Returns working day records for a given trainer
    public List<TrainerWorkingDay> getWorkingDays(int trainerId) {
        return trainerDAO.findWorkingDays(trainerId);
    }

    // Returns lesson slot records for a given trainer
    public List<TrainerLessonSlot> getLessonSlots(int trainerId) {
        return trainerDAO.findLessonSlots(trainerId);
    }

    // Resets a trainer's password — BCrypt hashing applied before storing
    public void resetTrainerPassword(int trainerId, String newPassword) {
        String hashed = AuthService.hashPassword(newPassword);
        trainerDAO.updatePassword(trainerId, hashed);
    }

    // Adds a new trainer account — password is hashed before insert
    public void addTrainer(Trainer trainer) {
        trainer.setPassword(AuthService.hashPassword(trainer.getPassword()));
        trainerDAO.insert(trainer);
    }

    // Saves working days for a trainer using delete + insert pattern
    // Validates that end time is after start time for each day
    public void saveWorkingDays(int trainerId, List<TrainerWorkingDay> days) {
        for (TrainerWorkingDay wd : days) {
            if (!wd.getEndTime().isAfter(wd.getStartTime()))
                throw new IllegalArgumentException(
                        wd.getDayOfWeek() + ": End time must be after start time.");
        }
        trainerDAO.saveWorkingDays(trainerId, days);
    }

    // Saves lesson slots for a trainer using delete + insert pattern
    // Validates: end > start for each slot, no duplicate slots, all slots within working hours
    public void saveLessonSlots(int trainerId, List<TrainerLessonSlot> slots) {

        for (TrainerLessonSlot slot : slots) {
            if (slot.getStartTime() == null || slot.getEndTime() == null)
                throw new IllegalArgumentException(
                        slot.getDayOfWeek() + ": Start and end time are required.");
            if (!slot.getEndTime().isAfter(slot.getStartTime()))
                throw new IllegalArgumentException(
                        slot.getDayOfWeek() + " " + slot.getStartTime()
                                + ": End time must be after start time.");
        }

        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                TrainerLessonSlot a = slots.get(i);
                TrainerLessonSlot b = slots.get(j);
                if (a.getDayOfWeek().equals(b.getDayOfWeek())
                        && a.getStartTime().equals(b.getStartTime())
                        && a.getEndTime().equals(b.getEndTime()))
                    throw new IllegalArgumentException(
                            "Duplicate slot: " + a.getDayOfWeek() + " " + a.getStartTime());
            }
        }

        List<TrainerWorkingDay> workingDays = trainerDAO.findWorkingDays(trainerId);
        for (TrainerLessonSlot slot : slots) {
            TrainerWorkingDay wd = workingDays.stream()
                    .filter(w -> w.getDayOfWeek().equals(slot.getDayOfWeek()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            slot.getDayOfWeek() + " is not a working day."));
            if (slot.getStartTime().isBefore(wd.getStartTime())
                    || slot.getEndTime().isAfter(wd.getEndTime()))
                throw new IllegalArgumentException(
                        "Slot must be within working hours: "
                                + wd.getStartTime() + " – " + wd.getEndTime());
        }
        trainerDAO.saveLessonSlots(trainerId, slots);
    }

    // Updates trainer info fields: full name, username, specialty
    // Optionally resets password if a new one is provided (not null or blank)
    public void updateTrainerInfo(int trainerId, String fullName,
                                  String username, String specialty, String newPassword) {
        trainerDAO.updateInfo(trainerId, fullName, username, specialty);
        if (newPassword != null && !newPassword.isBlank()) {
            String hashed = AuthService.hashPassword(newPassword);
            trainerDAO.updatePassword(trainerId, hashed);
        }
    }

    // Activates or deactivates a trainer
    public void setTrainerActive(int trainerId, boolean active) {
        trainerDAO.updateActive(trainerId, active);
    }

    // Unlocks a trainer account — resets lockout from failed login attempts
    public void unlockTrainer(int trainerId) {
        trainerDAO.updateLockStatus(trainerId, false);
    }

    // Checks whether a trainer's slot is already taken on a given date and time
    public boolean isSlotTaken(int trainerId, LocalDate date, LocalTime start, LocalTime end) {
        return appointmentDAO.isSlotTaken(trainerId, date, start, end);
    }
}