package com.iscms.dao;

import com.iscms.model.EventRegistration;
import java.util.List;

// DAO interface for event registration database operations
// Follows the DAO pattern: all SQL stays in the implementation, not in the service layer
public interface EventRegistrationDAO {

    // Insert a new event registration record into the database
    // DB has UNIQUE(member_id, event_id) — same member cannot register twice for the same event
    void insert(EventRegistration reg);

    // Set status = CANCELLED for a specific member's registration in a specific event
    // Called when a member cancels their own event registration
    void cancelByMemberAndEvent(int memberId, int eventId);

    // Set status = CANCELLED for all registrations of a given event
    // Called when a manager cancels an entire event
    void cancelAllByEventId(int eventId);

    // Check whether a member already has a registration record for a given event
    // Used to prevent duplicate registrations (REGISTERED or CANCELLED both count)
    boolean existsByMemberAndEvent(int memberId, int eventId);

    // Count the number of REGISTERED (active) participants for a given event
    // Used to enforce the event capacity quota
    int countRegistered(int eventId);

    // Return all registration records for a given event
    // Used by manager to view who has registered for an event
    List<EventRegistration> findByEventId(int eventId);

    // Return all registration records belonging to a given member
    // Used in member dashboard to show the member's event history
    List<EventRegistration> findByMemberId(int memberId);
}