package com.iscms.model;

import java.time.LocalDateTime;

// Model class representing a member's registration for an event
// Maps to the event_registration table in the database
// UNIQUE constraint in DB: (member_id, event_id) — one member can register once per event
public class EventRegistration {

    // Unique identifier for this registration record (maps to registration_id in DB)
    private int registrationId;

    // ID of the member who registered (FK → member table)
    private int memberId;

    // ID of the event the member registered for (FK → event table)
    private int eventId;

    // Timestamp of when the registration was made
    private LocalDateTime registrationDate;

    // Current status of the registration (REGISTERED or CANCELLED)
    private String status;

    // No-arg constructor required for JDBC result mapping
    public EventRegistration() {}

    // --- Getters and Setters ---

    public int getRegistrationId() { return registrationId; }
    public void setRegistrationId(int registrationId) { this.registrationId = registrationId; }

    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public LocalDateTime getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(LocalDateTime registrationDate) { this.registrationDate = registrationDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}