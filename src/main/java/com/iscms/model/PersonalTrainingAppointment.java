package com.iscms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Model class representing a personal training appointment (POJO)
// Maps to the personal_training_appointment table in the database
public class PersonalTrainingAppointment {

    // Unique identifier for this appointment (maps to appointment_id in DB)
    private int appointmentId;

    // ID of the member who booked this appointment (FK → member table)
    private int memberId;

    // ID of the trainer assigned to this appointment (FK → trainer table)
    private int trainerId;

    // Date the appointment is scheduled for
    private LocalDate appointmentDate;

    // Time the appointment session starts
    private LocalTime startTime;

    // Time the appointment session ends
    private LocalTime endTime;

    // Current status of the appointment: SCHEDULED, COMPLETED, CANCELLED, or NO_SHOW
    private String status;

    // Date until which the member is banned from booking new PT appointments
    // Set when trainer marks member as NO_SHOW (BR-34: 7-day booking ban)
    // LocalDate (not LocalDateTime) — only the date matters for this penalty check
    private LocalDate noShowPenaltyUntil;

    // Timestamp of when this appointment record was created
    private LocalDateTime createdAt;

    // No-arg constructor required for JDBC result mapping
    public PersonalTrainingAppointment() {}

    // --- Getters and Setters ---

    public int getAppointmentId() { return appointmentId; }
    public void setAppointmentId(int appointmentId) { this.appointmentId = appointmentId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getTrainerId() { return trainerId; }
    public void setTrainerId(int trainerId) { this.trainerId = trainerId; }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Null means no active penalty — checked at booking time in service layer
    public LocalDate getNoShowPenaltyUntil() { return noShowPenaltyUntil; }
    public void setNoShowPenaltyUntil(LocalDate noShowPenaltyUntil) { this.noShowPenaltyUntil = noShowPenaltyUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}