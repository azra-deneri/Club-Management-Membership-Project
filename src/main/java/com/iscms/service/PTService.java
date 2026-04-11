package com.iscms.service;

import com.iscms.dao.*;
import com.iscms.model.*;

import java.time.LocalDate;
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
    // 1. Member must have an active membership
    // 2. CLASSIC tier cannot book PT sessions
    // 3. Monthly session limit: GOLD = 2, VIP = 4
    // 4. Member must not have an active no-show penalty
    // 5. Trainer slot must not already be taken
    // 6. Member must not have a conflicting appointment at the same time
    public void bookAppointment(int memberId, int trainerId,
                                LocalDate date, LocalTime start, LocalTime end) {

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

    // Cancels a PT appointment
    // Cannot cancel an appointment that has already passed
    public void cancelAppointment(int appointmentId) {
        PersonalTrainingAppointment apt = appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found."));

        // Appointment date must be in the future
        if (!apt.getAppointmentDate().isAfter(LocalDate.now()))
            throw new IllegalStateException("Cannot cancel a past appointment.");

        appointmentDAO.updateStatus(appointmentId, "CANCELLED");
    }

    // Marks an appointment as COMPLETED — called by trainer after the session
    public void markCompleted(int appointmentId) {
        appointmentDAO.updateStatus(appointmentId, "COMPLETED");
    }

    // Marks an appointment as NO_SHOW and applies a 7-day booking penalty
    // The penalty blocks the member from booking new PT sessions for 7 days
    public void markNoShow(int appointmentId) {
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
    // Validates: no duplicate slots, all slots within working hours
    public void saveLessonSlots(int trainerId, List<TrainerLessonSlot> slots) {

        // Check for duplicate slots (same day + same time)
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

        // Check that each slot falls within the trainer's working hours for that day
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
    // Deactivated trainers cannot receive new PT appointments
    public void setTrainerActive(int trainerId, boolean active) {
        trainerDAO.updateActive(trainerId, active);
    }

    // Unlocks a trainer account — resets lockout from failed login attempts
    public void unlockTrainer(int trainerId) {
        trainerDAO.updateLockStatus(trainerId, false);
    }

    // Checks whether a trainer's slot is already taken on a given date and time
    // Used by PTPanel to display slot availability in the UI
    public boolean isSlotTaken(int trainerId, LocalDate date, LocalTime start, LocalTime end) {
        return appointmentDAO.isSlotTaken(trainerId, date, start, end);
    }
}