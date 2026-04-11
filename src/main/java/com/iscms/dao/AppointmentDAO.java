package com.iscms.dao;

import com.iscms.model.PersonalTrainingAppointment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// DAO interface for personal training appointment database operations
// Follows the DAO pattern: all SQL stays in the implementation, not in the service layer
public interface AppointmentDAO {

    // Insert a new personal training appointment record into the database
    void insert(PersonalTrainingAppointment apt);

    // Update the status of an appointment (SCHEDULED → COMPLETED, CANCELLED, or NO_SHOW)
    void updateStatus(int appointmentId, String status);

    // Set the no-show penalty expiry date on an appointment after a member is marked NO_SHOW
    // Penalty blocks the member from booking new PT appointments for 7 days
    void updateNoShowPenalty(int appointmentId, LocalDate penaltyUntil);

    // Find a single appointment by its ID — returns empty if not found
    Optional<PersonalTrainingAppointment> findById(int appointmentId);

    // Get all appointments belonging to a specific member
    List<PersonalTrainingAppointment> findByMemberId(int memberId);

    // Get all appointments assigned to a specific trainer
    List<PersonalTrainingAppointment> findByTrainerId(int trainerId);

    // Check if a trainer already has an appointment overlapping the given time slot
    // Used to prevent double-booking a trainer
    boolean isSlotTaken(int trainerId, LocalDate date, LocalTime startTime, LocalTime endTime);

    // Check if a member already has an appointment overlapping the given time slot
    // Used to prevent a member from booking two overlapping PT sessions
    boolean hasMemberConflict(int memberId, LocalDate date, LocalTime startTime, LocalTime endTime);

    // Count how many PT appointments a member has booked in a given month
    // Used to enforce monthly PT session limits based on membership tier
    int countMonthlyAppointments(int memberId, int year, int month);

    // Find the active no-show penalty expiry date for a member
    // Returns empty if the member has no active penalty
    // Used at booking time to block members under a no-show ban
    Optional<LocalDate> findActiveNoShowPenalty(int memberId);
}