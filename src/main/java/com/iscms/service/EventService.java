package com.iscms.service;

import com.iscms.dao.*;
import com.iscms.model.Event;
import com.iscms.model.EventRegistration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Service class responsible for all event-related business operations
// Handles event creation, editing, cancellation, capacity management, and member registration
public class EventService {

    private final EventDAO eventDAO;
    private final EventRegistrationDAO registrationDAO;

    // Default constructor — creates concrete DAO implementations
    public EventService() {
        this.eventDAO        = new EventDAOImpl();
        this.registrationDAO = new EventRegistrationDAOImpl();
    }

    // Constructor for unit testing — allows injecting mock DAO objects
    public EventService(EventDAO eventDAO, EventRegistrationDAO registrationDAO) {
        this.eventDAO        = eventDAO;
        this.registrationDAO = registrationDAO;
    }

    // Updates the editable details of an existing event
    // Cannot edit a cancelled event
    // Event date must be at least tomorrow — past dates not allowed
    // End time must be after start time
    public void updateEvent(int eventId, String name, String category,
                            java.time.LocalDate date, java.time.LocalTime start,
                            java.time.LocalTime end, String location, String description) {
        Event event = eventDAO.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        // Cancelled events cannot be edited
        if ("CANCELLED".equals(event.getStatus()))
            throw new IllegalStateException("Cannot edit a cancelled event.");

        // Event date must be in the future
        if (!date.isAfter(java.time.LocalDate.now()))
            throw new IllegalArgumentException("Event date must be at least tomorrow.");

        // End time must come after start time
        if (!end.isAfter(start))
            throw new IllegalArgumentException("End time must be after start time.");

        event.setEventName(name);
        event.setCategory(category);
        event.setEventDate(date);
        event.setStartTime(start);
        event.setEndTime(end);
        event.setLocation(location);
        event.setDescription(description);
        eventDAO.update(event);
    }

    // Creates a new event after validating required business rules
    // Event date must be at least tomorrow
    // End time must be after start time
    // Capacity must be a positive number
    // Status is always set to ACTIVE on creation
    public void createEvent(Event event) {
        if (!event.getEventDate().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Event date must be at least tomorrow.");
        if (!event.getEndTime().isAfter(event.getStartTime()))
            throw new IllegalArgumentException("End time must be after start time.");
        if (event.getCapacity() <= 0)
            throw new IllegalArgumentException("Capacity must be greater than 0.");
        event.setStatus("ACTIVE");
        eventDAO.insert(event);
    }

    // Cancels an event and all its member registrations in one operation
    // Called by manager when cancelling an event (UC-A06)
    public void cancelEvent(int eventId) {
        eventDAO.updateStatus(eventId, "CANCELLED");
        registrationDAO.cancelAllByEventId(eventId);
    }

    // Increases the capacity of an event
    // Cannot edit a cancelled event
    // Cannot increase capacity within 5 hours of the event start time
    // New capacity must be greater than the current capacity
    public void increaseCapacity(int eventId, int newCapacity) {
        Event event = eventDAO.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        // Cancelled events cannot have their capacity changed
        if ("CANCELLED".equals(event.getStatus()))
            throw new IllegalStateException("Cannot edit a cancelled event.");

        // Calculate the 5-hour deadline before event start
        LocalDateTime eventStart = event.getEventDate().atTime(event.getStartTime());
        if (LocalDateTime.now().isAfter(eventStart.minusHours(5)))
            throw new IllegalStateException(
                    "Capacity cannot be increased within 5 hours of the event.");

        // New capacity must be strictly greater than current capacity
        int currentCapacity = event.getCapacity();
        if (newCapacity <= currentCapacity)
            throw new IllegalArgumentException(
                    "New capacity must be greater than current capacity (" + currentCapacity + ").");

        eventDAO.updateCapacity(eventId, newCapacity);
    }

    // Registers a member for an event
    // Only ACTIVE members can register
    // Cannot register for a cancelled or past event
    // Cannot register if already registered for the same event
    // Cannot register if the event is at full capacity — no waitlist
    public void registerMember(int memberId, int eventId, String memberStatus) {

        // Only active members can register for events
        if (!"ACTIVE".equals(memberStatus))
            throw new IllegalStateException("Only active members can register for events.");

        Event event = eventDAO.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        // Cannot register for a cancelled event
        if ("CANCELLED".equals(event.getStatus()))
            throw new IllegalStateException("This event has been cancelled.");

        // Cannot register for a past event
        if (!event.getEventDate().isAfter(LocalDate.now()))
            throw new IllegalStateException("Cannot register for a past event.");

        // Cannot register if already registered for this event
        if (registrationDAO.existsByMemberAndEvent(memberId, eventId))
            throw new IllegalStateException("You are already registered for this event.");

        // Cannot register if event is at full capacity — no waitlist supported
        int registered = registrationDAO.countRegistered(eventId);
        if (registered >= event.getCapacity())
            throw new IllegalStateException(
                    "Event is full. No more registrations available.");

        EventRegistration reg = new EventRegistration();
        reg.setMemberId(memberId);
        reg.setEventId(eventId);
        registrationDAO.insert(reg);
    }

    // Cancels a member's registration for an event
    // Cannot cancel within 24 hours of the event start time
    public void cancelRegistration(int memberId, int eventId) {
        Event event = eventDAO.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        // Calculate the 24-hour cancellation deadline before event start
        LocalDateTime deadline = event.getEventDate()
                .atTime(event.getStartTime()).minusHours(24);
        if (LocalDateTime.now().isAfter(deadline))
            throw new IllegalStateException("Cannot cancel within 24 hours of the event.");

        registrationDAO.cancelByMemberAndEvent(memberId, eventId);
    }

    // Returns all events with status = ACTIVE
    // Used in member-facing event listing
    public List<Event> getActiveEvents() { return eventDAO.findActiveEvents(); }

    // Returns all events regardless of status
    // Used in manager-facing event management panel
    public List<Event> getAllEvents() { return eventDAO.findAll(); }

    // Returns a single event by ID
    public Optional<Event> getEventById(int id) { return eventDAO.findById(id); }

    // Returns the current number of registered participants for an event
    public int countRegistered(int eventId) {
        return registrationDAO.countRegistered(eventId);
    }

    // Returns all registration records for a given event
    // Used by manager to view participant list
    public List<EventRegistration> getRegistrationsByEvent(int eventId) {
        return registrationDAO.findByEventId(eventId);
    }

    // Returns all registration records for a given member
    // Used in member dashboard event history tab
    public List<EventRegistration> getRegistrationsByMember(int memberId) {
        return registrationDAO.findByMemberId(memberId);
    }

    // Marks all ACTIVE events whose date has passed as EXPIRED
    // Should be called at application startup or on a scheduled basis
    public void expirePastEvents() {
        for (Event event : eventDAO.findAll()) {
            if ("ACTIVE".equals(event.getStatus())
                    && !event.getEventDate().isAfter(LocalDate.now())) {
                eventDAO.updateStatus(event.getEventId(), "EXPIRED");
            }
        }
    }
}