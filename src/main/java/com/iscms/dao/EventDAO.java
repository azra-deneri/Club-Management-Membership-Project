package com.iscms.dao;

import com.iscms.model.Event;
import java.util.List;
import java.util.Optional;

// DAO interface for event database operations
// Follows the DAO pattern: all SQL stays in the implementation, not in the service layer
public interface EventDAO {

    // Insert a new event record into the database
    void insert(Event event);

    // Update the maximum participant capacity of an event
    // Manager can increase capacity up to 5 hours before the event starts
    void updateCapacity(int eventId, int newCapacity);

    // Update the status of an event
    // Valid status values: ACTIVE, CANCELLED, EXPIRED
    void updateStatus(int eventId, String status);

    // Update the editable details of an existing event
    // (name, category, date, time, location, description)
    void update(Event event);

    // Find a single event by its ID — returns empty if not found
    Optional<Event> findById(int eventId);

    // Return all events regardless of status
    List<Event> findAll();

    // Return only events with status = ACTIVE
    // Used in member-facing event listing
    List<Event> findActiveEvents();
}