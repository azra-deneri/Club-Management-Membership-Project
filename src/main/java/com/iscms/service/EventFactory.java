package com.iscms.service;

import com.iscms.model.Event;
import com.iscms.model.EventBuilder;

import java.time.LocalDate;
import java.time.LocalTime;

// Factory class responsible for creating Event objects
// Implements the Factory pattern — centralizes object creation logic
// Combines with Builder pattern: EventFactory calls EventBuilder internally
// This means validation (end > start, capacity > 0, etc.) is enforced at creation time
public class EventFactory {

    // Creates and returns a fully validated Event object
    // All required fields are passed as parameters — no partial construction possible
    // Delegates actual construction and validation to EventBuilder.build()
    public static Event createEvent(String name, String category,
                                    LocalDate date, LocalTime start, LocalTime end,
                                    String location, int capacity,
                                    String description, int managerId) {
        return new EventBuilder()
                .eventName(name)
                .category(category)
                .date(date)
                .startTime(start)
                .endTime(end)
                .location(location)
                .capacity(capacity)
                .description(description)
                .createdBy(managerId)
                .build(); // Validation runs here — throws IllegalStateException if invalid
    }
}