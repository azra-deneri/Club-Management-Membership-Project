package com.iscms;

import com.iscms.model.Event;
import com.iscms.service.EventFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for EventFactory — validates field assignment and Builder validation delegation
// EventFactory calls EventBuilder internally, so Builder rules are also tested here
// No mocks needed — Factory and Builder are pure Java with no external dependencies
public class EventFactoryTest {

    // All fields provided correctly — created event must have correct field values
    @Test
    void createEvent_fieldsSetCorrectly() {
        Event e = EventFactory.createEvent(
                "Yoga Class", "YOGA",
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Studio A", 20,
                "Morning yoga session", 1);

        assertEquals("Yoga Class", e.getEventName());
        assertEquals("YOGA", e.getCategory());
        assertEquals(20, e.getCapacity());
        assertEquals(1, e.getCreatedBy());
    }

    // Null description is optional — must not throw an exception
    @Test
    void createEvent_nullDescription_noException() {
        assertDoesNotThrow(() -> EventFactory.createEvent(
                "HIIT", "HIIT",
                LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(11, 0),
                "Gym", 30, null, 1));
    }

    // Zero capacity is invalid — Builder must throw IllegalStateException
    @Test
    void createEvent_zeroCapacity_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> EventFactory.createEvent(
                        "Test", "FITNESS",
                        LocalDate.now().plusDays(1),
                        LocalTime.of(10, 0), LocalTime.of(11, 0),
                        "Gym", 0, "desc", 1));
    }

    // End time before start time — Builder must throw IllegalStateException
    @Test
    void createEvent_endBeforeStart_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> EventFactory.createEvent(
                        "Test", "FITNESS",
                        LocalDate.now().plusDays(1),
                        LocalTime.of(11, 0), LocalTime.of(10, 0), // end before start
                        "Gym", 30, "desc", 1));
    }
}