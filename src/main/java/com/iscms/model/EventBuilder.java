package com.iscms.model;

import java.time.LocalDate;
import java.time.LocalTime;

// Builder pattern implementation for constructing Event objects
// Solves the problem of too many constructor parameters (constructor overload)
// Allows readable, step-by-step object creation with centralized validation
public class EventBuilder {

    // Internal Event instance being built — populated field by field
    private final Event event = new Event();

    // Sets the event name — returns 'this' to enable method chaining (fluent interface)
    public EventBuilder eventName(String name) {
        event.setEventName(name); return this;
    }

    // Sets the event category (e.g., FITNESS, YOGA, SWIMMING, HIIT, WORKSHOP, OTHER)
    public EventBuilder category(String category) {
        event.setCategory(category); return this;
    }

    // Sets the date the event will take place
    public EventBuilder date(LocalDate date) {
        event.setEventDate(date); return this;
    }

    // Sets the start time of the event
    public EventBuilder startTime(LocalTime startTime) {
        event.setStartTime(startTime); return this;
    }

    // Sets the end time of the event
    public EventBuilder endTime(LocalTime endTime) {
        event.setEndTime(endTime); return this;
    }

    // Sets the physical location or venue of the event
    public EventBuilder location(String location) {
        event.setLocation(location); return this;
    }

    // Sets the maximum number of participants allowed
    public EventBuilder capacity(int capacity) {
        event.setCapacity(capacity); return this;
    }

    // Sets an optional description or notes about the event
    public EventBuilder description(String description) {
        event.setDescription(description); return this;
    }

    // Sets the ID of the manager who is creating this event
    public EventBuilder createdBy(int managerId) {
        event.setCreatedBy(managerId); return this;
    }

    // Validates all required fields and returns the fully constructed Event object
    // Validation is centralized here — not scattered across the codebase
    public Event build() {
        // Start time and end time are both required
        if (event.getStartTime() == null || event.getEndTime() == null)
            throw new IllegalStateException("Start time and end time are required.");

        // End time must come after start time (business rule)
        if (!event.getEndTime().isAfter(event.getStartTime()))
            throw new IllegalStateException("End time must be after start time.");

        // Event name and date are required fields
        if (event.getEventName() == null || event.getEventName().trim().isEmpty() || event.getEventDate() == null)
            throw new IllegalStateException("Event name and date are required.");

        // Capacity must be a positive number
        if (event.getCapacity() <= 0)
            throw new IllegalStateException("Capacity must be greater than 0.");

        // All validations passed — return the constructed Event object
        return event;
    }
}