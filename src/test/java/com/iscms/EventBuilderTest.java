package com.iscms;

import com.iscms.model.Event;
import com.iscms.model.EventBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for EventBuilder — validates all required field and business rule checks
// No mocks needed — Builder pattern is pure Java with no external dependencies
public class EventBuilderTest {

    // All required fields provided — build must succeed and return correct values
    @Test
    void build_validEvent_success() {
        Event e = new EventBuilder()
                .eventName("Yoga Class")
                .category("FITNESS")
                .date(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .location("Studio A")
                .capacity(20)
                .description("Morning yoga")
                .createdBy(1)
                .build();

        assertEquals("Yoga Class", e.getEventName());
        assertEquals(20, e.getCapacity());
    }

    // Missing event name — build must throw IllegalStateException
    @Test
    void build_missingName_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                new EventBuilder()
                        .date(LocalDate.now().plusDays(1))
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(11, 0))
                        .capacity(10)
                        .build());
    }

    // Missing start time — build must throw IllegalStateException
    @Test
    void build_missingStartTime_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                new EventBuilder()
                        .eventName("Yoga")
                        .date(LocalDate.now().plusDays(1))
                        .endTime(LocalTime.of(11, 0))
                        .capacity(10)
                        .build());
    }

    // End time is before start time — build must throw IllegalStateException
    @Test
    void build_endTimeBeforeStartTime_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                new EventBuilder()
                        .eventName("Yoga")
                        .date(LocalDate.now().plusDays(1))
                        .startTime(LocalTime.of(11, 0))
                        .endTime(LocalTime.of(10, 0)) // end before start
                        .capacity(10)
                        .build());
    }

    // Zero capacity — build must throw IllegalStateException
    @Test
    void build_zeroCapacity_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                new EventBuilder()
                        .eventName("Yoga")
                        .date(LocalDate.now().plusDays(1))
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(11, 0))
                        .capacity(0) // must be > 0
                        .build());
    }
}