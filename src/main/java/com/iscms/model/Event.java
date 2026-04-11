package com.iscms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Model class representing a sports club event
public class Event {

    // Unique identifier for the event (maps to event_id in DB)
    private int eventId;

    // Name/title of the event (e.g., "Morning Yoga Class")
    private String eventName;

    // Category of the event (e.g., FITNESS, YOGA, SWIMMING, HIIT, WORKSHOP, OTHER)
    private String category;

    // Date the event takes place
    private LocalDate eventDate;

    // Time the event starts
    private LocalTime startTime;

    // Time the event ends
    private LocalTime endTime;

    // Physical location or venue of the event
    private String location;

    // Maximum number of participants allowed
    private int capacity;

    // Optional description or notes about the event
    private String description;

    // Current status of the event (ACTIVE or CANCELLED)
    private String status;

    // ID of the manager who created this event (FK → manager table)
    private int createdBy;

    // Timestamp of when the event record was created
    private LocalDateTime createdAt;

    // No-arg constructor required for JDBC result mapping
    public Event() {}

    // --- Getters and Setters ---

    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}