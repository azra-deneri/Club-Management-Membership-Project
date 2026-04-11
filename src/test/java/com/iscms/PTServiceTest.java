package com.iscms;

import com.iscms.dao.*;
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
import static org.mockito.Mockito.*;

// Unit tests for PTService — covers appointment booking, cancellation, no-show, and trainer management
// Uses Mockito to mock DAO interfaces — no real DB calls
@ExtendWith(MockitoExtension.class)
public class PTServiceTest {

    // Mocked DAO interfaces — injected into PTService via constructor
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

    // No active membership — booking must be blocked
    @Test
    void bookAppointment_noActiveMembership_throwsException() {
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1,
                        LocalDate.now().plusDays(1),
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    // CLASSIC tier cannot book PT sessions
    @Test
    void bookAppointment_classicMember_throwsException() {
        Membership ms = buildMembership("CLASSIC");
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1,
                        LocalDate.now().plusDays(1),
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    // GOLD tier monthly limit is 2 — exceeding it must throw
    @Test
    void bookAppointment_goldExceedsMonthlyLimit_throwsException() {
        Membership ms = buildMembership("GOLD");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(2); // at limit

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    // VIP tier monthly limit is 4 — exceeding it must throw
    @Test
    void bookAppointment_vipExceedsMonthlyLimit_throwsException() {
        Membership ms = buildMembership("VIP");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(4); // at limit

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    // VIP under monthly limit (3 of 4 used) — booking must succeed
    @Test
    void bookAppointment_vipUnderLimit_success() {
        Membership ms = buildMembership("VIP");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(3); // 3 of 4 used
        when(appointmentDAO.findActiveNoShowPenalty(1)).thenReturn(Optional.empty());
        when(appointmentDAO.isSlotTaken(anyInt(), any(), any(), any())).thenReturn(false);
        when(appointmentDAO.hasMemberConflict(anyInt(), any(), any(), any())).thenReturn(false);

        assertDoesNotThrow(() -> ptService.bookAppointment(1, 1, date,
                LocalTime.of(10, 0), LocalTime.of(11, 0)));
        verify(appointmentDAO).insert(any());
    }

    // Active no-show penalty blocks new bookings for 7 days
    @Test
    void bookAppointment_activeNoShowPenalty_throwsException() {
        Membership ms = buildMembership("GOLD");
        LocalDate date = LocalDate.now().plusDays(1);
        when(membershipDAO.findActiveByMemberId(1)).thenReturn(Optional.of(ms));
        when(appointmentDAO.countMonthlyAppointments(1,
                date.getYear(), date.getMonthValue())).thenReturn(0);
        when(appointmentDAO.findActiveNoShowPenalty(1))
                .thenReturn(Optional.of(LocalDate.now().plusDays(3))); // penalty active

        assertThrows(IllegalStateException.class,
                () -> ptService.bookAppointment(1, 1, date,
                        LocalTime.of(10, 0), LocalTime.of(11, 0)));
    }

    // Trainer slot already booked by another member — must throw
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

    // Member already has an overlapping appointment at the same time — must throw
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

    // --- Cancellation Tests ---

    // Past appointment cannot be cancelled
    @Test
    void cancelAppointment_pastAppointment_throwsException() {
        PersonalTrainingAppointment apt = new PersonalTrainingAppointment();
        apt.setAppointmentId(1);
        apt.setAppointmentDate(LocalDate.now().minusDays(1)); // yesterday
        apt.setStatus("SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertThrows(IllegalStateException.class,
                () -> ptService.cancelAppointment(1));
    }

    // Future appointment can be cancelled — must call updateStatus(CANCELLED)
    @Test
    void cancelAppointment_futureAppointment_success() {
        PersonalTrainingAppointment apt = new PersonalTrainingAppointment();
        apt.setAppointmentId(1);
        apt.setAppointmentDate(LocalDate.now().plusDays(2));
        apt.setStatus("SCHEDULED");
        when(appointmentDAO.findById(1)).thenReturn(Optional.of(apt));

        assertDoesNotThrow(() -> ptService.cancelAppointment(1));
        verify(appointmentDAO).updateStatus(1, "CANCELLED");
    }

    // --- No-Show and Complete Tests ---

    // markNoShow must set status to NO_SHOW and apply 7-day booking penalty
    @Test
    void markNoShow_setsStatusAndPenalty() {
        ptService.markNoShow(1);
        verify(appointmentDAO).updateStatus(1, "NO_SHOW");
        verify(appointmentDAO).updateNoShowPenalty(eq(1),
                eq(LocalDate.now().plusDays(7)));
    }

    // markCompleted must set status to COMPLETED
    @Test
    void markCompleted_setsStatusCompleted() {
        ptService.markCompleted(1);
        verify(appointmentDAO).updateStatus(1, "COMPLETED");
    }

    // --- Trainer Management Tests ---

    // addTrainer must hash the plain text password before calling DAO insert
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

    // saveWorkingDays with valid days — must call trainerDAO.saveWorkingDays()
    @Test
    void saveWorkingDays_callsDAO() {
        List<TrainerWorkingDay> days = List.of(buildWorkingDay("MONDAY"));
        ptService.saveWorkingDays(1, days);
        verify(trainerDAO).saveWorkingDays(1, days);
    }

    // saveWorkingDays with end before start — must throw IllegalArgumentException
    @Test
    void saveWorkingDays_endBeforeStart_throwsException() {
        TrainerWorkingDay wd = new TrainerWorkingDay();
        wd.setTrainerId(1);
        wd.setDayOfWeek("MONDAY");
        wd.setStartTime(LocalTime.of(18, 0));
        wd.setEndTime(LocalTime.of(9, 0)); // end before start

        assertThrows(IllegalArgumentException.class,
                () -> ptService.saveWorkingDays(1, List.of(wd)));
    }

    // saveLessonSlots with valid slot within working hours — must call DAO
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

    // saveLessonSlots with duplicate slot — duplicate check runs before working hours check
    @Test
    void saveLessonSlots_duplicateSlot_throwsException() {
        TrainerLessonSlot slot1 = new TrainerLessonSlot();
        slot1.setDayOfWeek("MONDAY");
        slot1.setStartTime(LocalTime.of(9, 0));
        slot1.setEndTime(LocalTime.of(10, 0));

        TrainerLessonSlot slot2 = new TrainerLessonSlot();
        slot2.setDayOfWeek("MONDAY");
        slot2.setStartTime(LocalTime.of(9, 0));
        slot2.setEndTime(LocalTime.of(10, 0)); // exact duplicate

        // No mock needed — duplicate check fires before working hours check
        assertThrows(IllegalArgumentException.class,
                () -> ptService.saveLessonSlots(1, List.of(slot1, slot2)));
    }

    // saveLessonSlots with slot outside working hours — must throw
    @Test
    void saveLessonSlots_outsideWorkingHours_throwsException() {
        TrainerWorkingDay wd = new TrainerWorkingDay();
        wd.setDayOfWeek("MONDAY");
        wd.setStartTime(LocalTime.of(9, 0));
        wd.setEndTime(LocalTime.of(18, 0));
        when(trainerDAO.findWorkingDays(1)).thenReturn(List.of(wd));

        TrainerLessonSlot slot = new TrainerLessonSlot();
        slot.setDayOfWeek("MONDAY");
        slot.setStartTime(LocalTime.of(7, 0)); // before working hours start
        slot.setEndTime(LocalTime.of(8, 0));

        assertThrows(IllegalArgumentException.class,
                () -> ptService.saveLessonSlots(1, List.of(slot)));
    }

    // resetTrainerPassword must hash the new password before calling updatePassword
    @Test
    void resetTrainerPassword_hashesAndCallsDAO() {
        ptService.resetTrainerPassword(1, "newpassword");
        verify(trainerDAO).updatePassword(eq(1),
                argThat(hash -> hash.startsWith("$2a$")));
    }

    // setTrainerActive must call trainerDAO.updateActive() with correct arguments
    @Test
    void setTrainerActive_callsDAO() {
        ptService.setTrainerActive(1, false);
        verify(trainerDAO).updateActive(1, false);
    }

    // unlockTrainer must call trainerDAO.updateLockStatus(false)
    @Test
    void unlockTrainer_callsDAO() {
        ptService.unlockTrainer(1);
        verify(trainerDAO).updateLockStatus(1, false);
    }

    // --- Helper Methods ---

    // Builds a Membership test object with the given tier
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

    // Builds a TrainerWorkingDay test object for the given day
    private TrainerWorkingDay buildWorkingDay(String day) {
        TrainerWorkingDay wd = new TrainerWorkingDay();
        wd.setTrainerId(1);
        wd.setDayOfWeek(day);
        wd.setStartTime(LocalTime.of(9, 0));
        wd.setEndTime(LocalTime.of(17, 0));
        return wd;
    }

    // Builds a TrainerLessonSlot test object for the given day
    private TrainerLessonSlot buildLessonSlot(String day) {
        TrainerLessonSlot slot = new TrainerLessonSlot();
        slot.setTrainerId(1);
        slot.setDayOfWeek(day);
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setEndTime(LocalTime.of(10, 0));
        return slot;
    }
}