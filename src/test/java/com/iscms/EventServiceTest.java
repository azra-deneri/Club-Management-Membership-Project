package com.iscms;

import com.iscms.dao.EventDAO;
import com.iscms.dao.EventRegistrationDAO;
import com.iscms.model.Event;
import com.iscms.service.EventService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Unit tests for EventService — covers event creation, registration, capacity, and expiry
// Uses Mockito to mock DAO interfaces — no real DB calls
@ExtendWith(MockitoExtension.class)
public class EventServiceTest {

    // Mocked DAO interfaces — injected into EventService via constructor
    @Mock private EventDAO eventDAO;
    @Mock private EventRegistrationDAO registrationDAO;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventDAO, registrationDAO);
    }

    // --- createEvent Tests ---

    // Event date in the past — must throw IllegalArgumentException
    @Test
    void createEvent_pastDate_throwsException() {
        Event event = buildEvent(LocalDate.now().minusDays(1), 50);
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(event));
    }

    // Zero capacity is invalid — must throw IllegalArgumentException
    @Test
    void createEvent_zeroCapacity_throwsException() {
        Event event = buildEvent(LocalDate.now().plusDays(1), 0);
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(event));
    }

    // Valid event — must call DAO insert exactly once
    @Test
    void createEvent_validEvent_callsInsert() {
        Event event = buildEvent(LocalDate.now().plusDays(1), 50);
        eventService.createEvent(event);
        verify(eventDAO).insert(event);
    }

    // --- registerMember Tests ---

    // Non-ACTIVE member status — registration must be blocked
    @Test
    void registerMember_inactiveMember_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> eventService.registerMember(1, 1, "PASSIVE"));
    }

    // Event is at full capacity — registration must be blocked
    @Test
    void registerMember_eventFull_throwsException() {
        Event event = buildEvent(LocalDate.now().plusDays(1), 10);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));
        when(registrationDAO.existsByMemberAndEvent(1, 1)).thenReturn(false);
        when(registrationDAO.countRegistered(1)).thenReturn(10); // capacity = 10, registered = 10

        assertThrows(IllegalStateException.class,
                () -> eventService.registerMember(1, 1, "ACTIVE"));
    }

    // Member already has an active registration for this event
    @Test
    void registerMember_alreadyRegistered_throwsException() {
        Event event = buildEvent(LocalDate.now().plusDays(1), 50);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));
        when(registrationDAO.existsByMemberAndEvent(1, 1)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> eventService.registerMember(1, 1, "ACTIVE"));
    }

    // Event date is in the past — registration must be blocked
    @Test
    void registerMember_pastEvent_throwsException() {
        Event event = new Event();
        event.setStatus("ACTIVE");
        event.setEventDate(LocalDate.now().minusDays(1));
        event.setCapacity(10);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));

        assertThrows(IllegalStateException.class,
                () -> eventService.registerMember(1, 1, "ACTIVE"));
    }

    // All conditions met — must call registrationDAO.insert()
    @Test
    void registerMember_success_callsInsert() {
        Event event = buildEvent(LocalDate.now().plusDays(1), 50);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));
        when(registrationDAO.existsByMemberAndEvent(1, 1)).thenReturn(false);
        when(registrationDAO.countRegistered(1)).thenReturn(5); // 5 < 50 capacity

        eventService.registerMember(1, 1, "ACTIVE");

        verify(registrationDAO).insert(any());
    }

    // --- increaseCapacity Tests ---

    // New capacity is less than current — must throw IllegalArgumentException
    @Test
    void increaseCapacity_lessThanCurrent_throwsException() {
        Event event = buildEvent(LocalDate.now().plusDays(2), 50);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));

        assertThrows(IllegalArgumentException.class,
                () -> eventService.increaseCapacity(1, 30)); // 30 < 50
    }

    // Within 5 hours of event start — capacity increase must be blocked
    @Test
    void increaseCapacity_within5Hours_throwsException() {
        Event event = buildEvent(LocalDate.now(), 50);
        event.setStartTime(LocalTime.now().plusHours(2)); // only 2 hours away
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));

        assertThrows(IllegalStateException.class,
                () -> eventService.increaseCapacity(1, 100));
    }

    // Valid capacity increase — must call DAO updateCapacity
    @Test
    void increaseCapacity_valid_callsUpdate() {
        Event event = buildEvent(LocalDate.now().plusDays(2), 50);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));

        eventService.increaseCapacity(1, 100);

        verify(eventDAO).updateCapacity(1, 100);
    }

    // --- expirePastEvents Tests ---

    // Past ACTIVE event — must be set to EXPIRED; future event must not be affected
    @Test
    void expirePastEvents_setsExpiredStatus() {
        Event pastEvent   = buildEvent(LocalDate.now().minusDays(1), 50);
        Event futureEvent = buildEvent(LocalDate.now().plusDays(1), 50);
        when(eventDAO.findAll()).thenReturn(java.util.List.of(pastEvent, futureEvent));

        eventService.expirePastEvents();

        verify(eventDAO).updateStatus(1, "EXPIRED");
        verify(eventDAO, never()).updateStatus(eq(1), eq("ACTIVE"));
    }

    // Future event only — no status updates should occur
    @Test
    void expirePastEvents_futureEventsNotAffected() {
        Event futureEvent = buildEvent(LocalDate.now().plusDays(5), 50);
        futureEvent.setEventId(2);
        when(eventDAO.findAll()).thenReturn(java.util.List.of(futureEvent));

        eventService.expirePastEvents();

        verify(eventDAO, never()).updateStatus(anyInt(), anyString());
    }

    // --- updateEvent Tests ---

    // Valid edit — must call DAO update without throwing
    @Test
    void updateEvent_validEdit_callsUpdate() {
        Event event = buildEvent(LocalDate.now().plusDays(2), 50);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));

        assertDoesNotThrow(() -> eventService.updateEvent(
                1, "New Name", "YOGA",
                LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), LocalTime.of(11, 0),
                "New Location", "New Description"));

        verify(eventDAO).update(any());
    }

    // Cancelled event cannot be edited — must throw IllegalStateException
    @Test
    void updateEvent_cancelledEvent_throwsException() {
        Event event = buildEvent(LocalDate.now().plusDays(2), 50);
        event.setStatus("CANCELLED");
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));

        assertThrows(IllegalStateException.class,
                () -> eventService.updateEvent(
                        1, "New Name", "YOGA",
                        LocalDate.now().plusDays(3),
                        LocalTime.of(10, 0), LocalTime.of(11, 0),
                        "Location", "Desc"));
    }

    // Past date provided for update — must throw IllegalArgumentException
    @Test
    void updateEvent_pastDate_throwsException() {
        Event event = buildEvent(LocalDate.now().plusDays(2), 50);
        when(eventDAO.findById(1)).thenReturn(Optional.of(event));

        assertThrows(IllegalArgumentException.class,
                () -> eventService.updateEvent(
                        1, "Name", "FITNESS",
                        LocalDate.now().minusDays(1), // past date
                        LocalTime.of(10, 0), LocalTime.of(11, 0),
                        "Location", "Desc"));
    }

    // --- Helper ---

    // Builds a test Event object with the given date and capacity
    private Event buildEvent(LocalDate date, int capacity) {
        Event e = new Event();
        e.setEventId(1);
        e.setEventName("Test Event");
        e.setCategory("FITNESS");
        e.setEventDate(date);
        e.setStartTime(LocalTime.of(10, 0));
        e.setEndTime(LocalTime.of(11, 0));
        e.setCapacity(capacity);
        e.setStatus("ACTIVE");
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}