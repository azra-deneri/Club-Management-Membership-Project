package com.iscms;

import com.iscms.dao.AppointmentDAO;
import com.iscms.dao.MemberDAO;
import com.iscms.dao.MembershipDAO;
import com.iscms.dao.TrainerDAO;
import com.iscms.model.*;
import com.iscms.service.PTService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

// Unit tests for PTService — covers booking, cancellation, lifecycle, and trainer ops.
// Batch 1 added time/status guards to cancelAppointment, markCompleted, markNoShow,
// so tests for those three methods now mock findById and supply realistic appointment
// shapes (status=SCHEDULED + time relationship that satisfies the guard).
@ExtendWith(MockitoExtension.class)
public class PTServiceTest {

    @Mock private AppointmentDAO appointmentDAO;
    @Mock private TrainerDAO trainerDAO;
    @Mock private MemberDAO memberDAO;
    @Mock private MembershipDAO membershipDAO;

    private PTService ptService;

    @BeforeEach
    void setUp() {
        ptService = new PTService(appointmentDAO, trainerDAO, memberDAO, membershipDAO);
    }

    // --- Booking Tests ---

    @Test
    void bookAppointment_noActiveMembership_throwsException() {
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1,
                        LocalDate.now().plusDays(1),
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    void bookAppointment_classicMember_throwsException() {
        Membership ms = buildMembership("CLASSIC");
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1,
                        LocalDate.now().plusDays(1),
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    void bookAppointment_goldExceedsMonthlyLimit_throwsException() {
        Membership ms = buildMembership("GOLD");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(2);

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    void bookAppointment_vipExceedsMonthlyLimit_throwsException() {
        Membership ms = buildMembership("VIP");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(4);

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    void bookAppointment_vipUnderLimit_success() {
        Membership ms = buildMembership("VIP");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(3);
        when(appointmentDAO.findActiveNoShowPenalty(1)).thenReturn(Optional.empty());
        when(appointmentDAO.isSlotTaken(anyInt(), any(), any(), any())).thenReturn(false);
        when(appointmentDAO.hasMemberConflict(anyInt(), any(), any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> ptService.bookAppointment(1, 1, date,
                LocalTime.of(10, 0), LocalTime.of(11, 0)));
        verify(appointmentDAO).insert(any());
    }

    @Test
    void bookAppointment_activeNoShowPenalty_throwsException() {
        Membership ms = buildMembership("GOLD");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(0);
        when(appointmentDAO.findActiveNoShowPenalty(1))
                .thenReturn(Optional.of(LocalDate.now().plusDays(3)));

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    void bookAppointment_slotAlreadyTaken_throwsException() {
        Membership ms = buildMembership("GOLD");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(0);
        when(appointmentDAO.findActiveNoShowPenalty(1)).thenReturn(Optional.empty());
        when(appointmentDAO.isSlotTaken(anyInt(), any(), any(), any())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    @Test
    void bookAppointment_memberConflict_throwsException() {
        Membership ms = buildMembership("GOLD");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(0);
        when(appointmentDAO.findActiveNoShowPenalty(1)).thenReturn(Optional.empty());
        when(appointmentDAO.isSlotTaken(anyInt(), any(), any(), any())).thenReturn(false);
        when(appointmentDAO.hasMemberConflict(anyInt(), any(), any(), any())).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    // Booking with end ≤ start must be rejected before any DB call (Batch 1)
    @Test
    void bookAppointment_endBeforeStart_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> ptService.bookAppointment(1, 1,
                        LocalDate.now().plusDays(1),
                        LocalTime.of(11, 0), LocalTime.of(10, 0))); // reversed
    }

    // --- Cancellation Tests ---
    // Batch 1: cancelAppointment now requires status=SCHEDULED and refuses past-start times.

    // Already-started appointment (yesterday) cannot be cancelled
    @Test
    void cancelAppointment_pastAppointment_throwsException() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertThrows(IllegalStateException.class,
                () -> ptService.cancelAppointment(1));
    }

    // Future appointment with status=SCHEDULED is cancellable
    @Test
    void cancelAppointment_futureAppointment_success() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().plusDays(2),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertDoesNotThrow(() -> ptService.cancelAppointment(1));
        verify(appointmentDAO).updateStatus(1, "CANCELLED");
    }

    // Cancelled appointment cannot be cancelled again — status guard fires (Batch 1)
    @Test
    void cancelAppointment_alreadyCancelled_throwsException() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().plusDays(2),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CANCELLED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertThrows(IllegalStateException.class,
                () -> ptService.cancelAppointment(1));
    }

    // --- No-Show and Complete Tests ---
    // Batch 1: both methods now require status=SCHEDULED and have time guards.
    //   markCompleted: appointment must have started
    //   markNoShow: appointment end + 1-hour grace must have passed

    // markNoShow on a finished appointment (status=SCHEDULED, end > 1h ago) must succeed
    @Test
    void markNoShow_setsStatusAndPenalty() {
        // Yesterday 10:00–11:00 — well past the 1-hour grace window
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        ptService.markNoShow(1);

        verify(appointmentDAO).updateStatus(1, "NO_SHOW");
        verify(appointmentDAO).updateNoShowPenalty(eq(1),
                eq(LocalDate.now().plusDays(7)));
    }

    // markNoShow on a future appointment must throw — grace period not yet elapsed
    @Test
    void markNoShow_futureAppointment_throwsException() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().plusDays(2),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertThrows(IllegalStateException.class,
                () -> ptService.markNoShow(1));
    }

    // markNoShow on a cancelled appointment must throw — status guard fires
    @Test
    void markNoShow_alreadyCancelled_throwsException() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CANCELLED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertThrows(IllegalStateException.class,
                () -> ptService.markNoShow(1));
    }

    // markCompleted on a started appointment (yesterday) must succeed
    @Test
    void markCompleted_setsStatusCompleted() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        ptService.markCompleted(1);

        verify(appointmentDAO).updateStatus(1, "COMPLETED");
    }

    // markCompleted on a future appointment must throw — session hasn't started
    @Test
    void markCompleted_futureAppointment_throwsException() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().plusDays(2),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertThrows(IllegalStateException.class,
                () -> ptService.markCompleted(1));
    }

    // markCompleted on a cancelled appointment must throw — status guard fires
    @Test
    void markCompleted_alreadyCancelled_throwsException() {
        PersonalTrainingAppointment apt = buildAppointment(
                LocalDate.now().minusDays(1),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "CANCELLED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertThrows(IllegalStateException.class,
                () -> ptService.markCompleted(1));
    }

    // --- Trainer Management Tests ---

    @Test
    void addTrainer_hashesPasswordAndCallsInsert() {
        Trainer t = new Trainer();
        t.setFullName("Test Trainer");
        t.setUsername("testtrainer");
        t.setPassword("plainpassword");
        t.setActive(true);

        ptService.addTrainer(t);

        verify(trainerDAO).insert(argThat(trainer ->
                trainer.getPassword() != null &&
                        trainer.getPassword().startsWith("$2a$")
        ));
    }

    @Test
    void saveWorkingDays_callsDAO() {
        List<TrainerWorkingDay> days = List.of(buildWorkingDay("MONDAY"));
        ptService.saveWorkingDays(1, days);
        verify(trainerDAO).saveWorkingDays(1, days);
    }

    @Test
    void saveWorkingDays_endBeforeStart_throwsException() {
        TrainerWorkingDay wd = new TrainerWorkingDay();
        wd.setTrainerId(1);
        wd.setDayOfWeek("MONDAY");
        wd.setStartTime(LocalTime.of(18, 0));
        wd.setEndTime(LocalTime.of(9, 0));

        assertThrows(IllegalArgumentException.class,
                () -> ptService.saveWorkingDays(1, List.of(wd)));
    }

    @Test
    void saveLessonSlots_callsDAO() {
        TrainerWorkingDay wd = new TrainerWorkingDay();
        wd.setDayOfWeek("MONDAY");
        wd.setStartTime(LocalTime.of(9, 0));
        wd.setEndTime(LocalTime.of(17, 0));
        when(trainerDAO.findWorkingDays(1)).thenReturn(List.of(wd));

        List<TrainerLessonSlot> slots = List.of(buildLessonSlot("MONDAY"));
        ptService.saveLessonSlots(1, slots);
        verify(trainerDAO).saveLessonSlots(1, slots);
    }

    @Test
    void saveLessonSlots_duplicateSlot_throwsException() {
        TrainerLessonSlot slot1 = new TrainerLessonSlot();
        slot1.setDayOfWeek("MONDAY");
        slot1.setStartTime(LocalTime.of(9, 0));
        slot1.setEndTime(LocalTime.of(10, 0));

        TrainerLessonSlot slot2 = new TrainerLessonSlot();
        slot2.setDayOfWeek("MONDAY");
        slot2.setStartTime(LocalTime.of(9, 0));
        slot2.setEndTime(LocalTime.of(10, 0));

        assertThrows(IllegalArgumentException.class,
                () -> ptService.saveLessonSlots(1, List.of(slot1, slot2)));
    }

    @Test
    void saveLessonSlots_outsideWorkingHours_throwsException() {
        TrainerWorkingDay wd = new TrainerWorkingDay();
        wd.setDayOfWeek("MONDAY");
        wd.setStartTime(LocalTime.of(9, 0));
        wd.setEndTime(LocalTime.of(18, 0));
        when(trainerDAO.findWorkingDays(1)).thenReturn(List.of(wd));

        TrainerLessonSlot slot = new TrainerLessonSlot();
        slot.setDayOfWeek("MONDAY");
        slot.setStartTime(LocalTime.of(7, 0));
        slot.setEndTime(LocalTime.of(8, 0));

        assertThrows(IllegalArgumentException.class,
                () -> ptService.saveLessonSlots(1, List.of(slot)));
    }

    @Test
    void resetTrainerPassword_hashesAndCallsDAO() {
        ptService.resetTrainerPassword(1, "newpassword");
        verify(trainerDAO).updatePassword(eq(1),
                argThat(hash -> hash.startsWith("$2a$")));
    }

    @Test
    void setTrainerActive_callsDAO() {
        ptService.setTrainerActive(1, false);
        verify(trainerDAO).updateActive(1, false);
    }

    @Test
    void unlockTrainer_callsDAO() {
        ptService.unlockTrainer(1);
        verify(trainerDAO).updateLockStatus(1, false);
    }

    // --- Helper Methods ---

    private Membership buildMembership(String tier) {
        Membership ms = new Membership();
        ms.setMembershipId(1);
        ms.setMemberId(1);
        ms.setTier(tier);
        ms.setStatus("ACTIVE");
        ms.setPackageType("MONTHLY");
        ms.setStartDate(LocalDate.now().minusDays(10));
        ms.setEndDate(LocalDate.now().plusDays(20));
        ms.setFreezeCount(0);
        return ms;
    }

    private TrainerWorkingDay buildWorkingDay(String day) {
        TrainerWorkingDay wd = new TrainerWorkingDay();
        wd.setTrainerId(1);
        wd.setDayOfWeek(day);
        wd.setStartTime(LocalTime.of(9, 0));
        wd.setEndTime(LocalTime.of(17, 0));
        return wd;
    }

    private TrainerLessonSlot buildLessonSlot(String day) {
        TrainerLessonSlot slot = new TrainerLessonSlot();
        slot.setTrainerId(1);
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        return slot;
    }

    // Builds a PersonalTrainingAppointment with all the fields required by Batch 1
    // time/status guards. Used by cancel/markCompleted/markNoShow tests.
    private PersonalTrainingAppointment buildAppointment(LocalDate date,
                                                         LocalTime start,
                                                         LocalTime end,
                                                         String status) {
        PersonalTrainingAppointment apt = new PersonalTrainingAppointment();
        apt.setAppointmentId(1);
        apt.setMemberId(1);
        apt.setTrainerId(1);
        apt.setAppointmentDate(date);
        apt.setStartTime(start);
        apt.setEndTime(end);
        apt.setStatus(status);
        return apt;
    }

    // --- Trainer-action eligibility helpers (G4) ---
    // canMarkOutcome and canTrainerCancel encode the "can the trainer act on
    // this appointment?" rules. These were previously inline in the controller
    // and the view template — pulled into PTService so both layers can rely
    // on the same authoritative answer.

    @Test
    void canMarkOutcome_scheduledAndPast_returnsTrue() {
        PersonalTrainingAppointment a = new PersonalTrainingAppointment();
        a.setStatus("SCHEDULED");
        a.setAppointmentDate(LocalDate.of(2026, 5, 1));

        assertTrue(ptService.canMarkOutcome(a, LocalDate.of(2026, 5, 26)));
    }

    @Test
    void canMarkOutcome_scheduledAndToday_returnsTrue() {
        PersonalTrainingAppointment a = new PersonalTrainingAppointment();
        a.setStatus("SCHEDULED");
        LocalDate today = LocalDate.of(2026, 5, 26);
        a.setAppointmentDate(today);

        assertTrue(ptService.canMarkOutcome(a, today));
    }

    @Test
    void canMarkOutcome_scheduledButFuture_returnsFalse() {
        PersonalTrainingAppointment a = new PersonalTrainingAppointment();
        a.setStatus("SCHEDULED");
        a.setAppointmentDate(LocalDate.of(2026, 6, 10));

        assertFalse(ptService.canMarkOutcome(a, LocalDate.of(2026, 5, 26)));
    }

    @Test
    void canMarkOutcome_alreadyCompleted_returnsFalse() {
        PersonalTrainingAppointment a = new PersonalTrainingAppointment();
        a.setStatus("COMPLETED");
        a.setAppointmentDate(LocalDate.of(2026, 5, 1));

        assertFalse(ptService.canMarkOutcome(a, LocalDate.of(2026, 5, 26)));
    }

    @Test
    void canTrainerCancel_scheduledRegardlessOfDate_returnsTrue() {
        PersonalTrainingAppointment future = new PersonalTrainingAppointment();
        future.setStatus("SCHEDULED");
        future.setAppointmentDate(LocalDate.of(2026, 7, 1));

        PersonalTrainingAppointment past = new PersonalTrainingAppointment();
        past.setStatus("SCHEDULED");
        past.setAppointmentDate(LocalDate.of(2026, 4, 1));

        assertTrue(ptService.canTrainerCancel(future));
        assertTrue(ptService.canTrainerCancel(past));
    }

    @Test
    void canTrainerCancel_alreadyCancelledOrCompleted_returnsFalse() {
        PersonalTrainingAppointment cancelled = new PersonalTrainingAppointment();
        cancelled.setStatus("CANCELLED");

        PersonalTrainingAppointment completed = new PersonalTrainingAppointment();
        completed.setStatus("COMPLETED");

        assertFalse(ptService.canTrainerCancel(cancelled));
        assertFalse(ptService.canTrainerCancel(completed));
    }
}