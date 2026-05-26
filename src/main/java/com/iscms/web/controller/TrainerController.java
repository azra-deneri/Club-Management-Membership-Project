package com.iscms.web.controller;

import com.iscms.model.PersonalTrainingAppointment;
import com.iscms.model.Trainer;
import com.iscms.model.Member;
import com.iscms.model.TrainerLessonSlot;
import com.iscms.model.TrainerWorkingDay;
import com.iscms.service.AuthService;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;
import com.iscms.web.dto.DtoMapper;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Trainer-facing endpoints. Trainers can:
 *  - view their weekly schedule (working days + lesson slots)
 *  - see their appointments (upcoming, past, cancelled)
 *  - update their own profile (password change)
 *
 * Trainers cannot edit working days or lesson slots — only the manager can.
 *
 * Week 14 refactor:
 *  - The three GET endpoints that previously wrote a raw Trainer entity into
 *    the model now write a TrainerDTO instead. The BCrypt password hash,
 *    failedAttempts counter, and isLocked flag carried by the entity can no
 *    longer surface in any Thymeleaf template, even by accident.
 *  - currentTrainer() is kept as a null-returning guard rather than an
 *    exception-throwing variant. Trainer flows are short and infrequent, and
 *    the redirect-on-null pattern is already consistent across the file —
 *    no duplication worth removing here.
 */
@Controller
@RequestMapping("/trainer")
public class TrainerController {

    private final PTService ptService;
    private final AuthService authService;
    private final MemberService memberService;

    public TrainerController(PTService ptService,
                             AuthService authService,
                             MemberService memberService) {
        this.ptService = ptService;
        this.authService = authService;
        this.memberService = memberService;
    }

    /** Helper: guard every page; returns logged-in Trainer or null. */
    private Trainer currentTrainer(HttpSession session) {
        Object user = session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (user instanceof Trainer t && "TRAINER".equals(role)) {
            return t;
        }
        return null;
    }

    // ===================== DASHBOARD (Weekly Schedule) =====================

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String weekStart,
                            HttpSession session, Model model) {
        Trainer trainer = currentTrainer(session);
        if (trainer == null) return "redirect:/login";

        // Build weekly schedule: each day -> list of lesson slots with booking status.
        LocalDate today = LocalDate.now();
        LocalDate currentMonday = today.with(DayOfWeek.MONDAY);

        // weekStart query param lets trainer navigate prev/next weeks.
        // Snap any incoming date to the Monday of its week for safety.
        LocalDate monday;
        if (weekStart != null && !weekStart.isBlank()) {
            try { monday = LocalDate.parse(weekStart.trim()).with(DayOfWeek.MONDAY); }
            catch (Exception ex) { monday = currentMonday; }
        } else {
            monday = currentMonday;
        }

        List<TrainerLessonSlot> allSlots = ptService.getLessonSlots(trainer.getTrainerId());
        List<TrainerWorkingDay> workingDays = ptService.getWorkingDays(trainer.getTrainerId());
        List<PersonalTrainingAppointment> appointments =
                ptService.getTrainerAppointments(trainer.getTrainerId());

        // Build memberId -> full name map once (used by today's card AND booked slots).
        // Lookup goes through MemberService — controller does NOT touch DAOs directly.
        Map<Integer, String> memberNames = new HashMap<>();
        for (PersonalTrainingAppointment appt : appointments) {
            if (!memberNames.containsKey(appt.getMemberId())) {
                memberNames.put(appt.getMemberId(),
                        memberService.getMemberById(appt.getMemberId())
                                .map(Member::getFullName)
                                .orElse("Member #" + appt.getMemberId()));
            }
        }

        // Group slots by day-of-week (MONDAY, TUESDAY, ...)
        List<DayView> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            String dow = date.getDayOfWeek().name();
            String dayLabel = date.getDayOfWeek()
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            List<TrainerLessonSlot> daySlots = allSlots.stream()
                    .filter(s -> dow.equals(s.getDayOfWeek()))
                    .sorted(Comparator.comparing(TrainerLessonSlot::getStartTime))
                    .toList();

            List<SlotView> slotViews = new ArrayList<>();
            for (TrainerLessonSlot slot : daySlots) {
                PersonalTrainingAppointment booked = appointments.stream()
                        .filter(a -> date.equals(a.getAppointmentDate())
                                && slot.getStartTime().equals(a.getStartTime())
                                && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                        .findFirst()
                        .orElse(null);

                String status;
                if (booked != null) {
                    status = "BOOKED";
                } else if (date.isBefore(today)) {
                    status = "PAST";
                } else {
                    status = "AVAILABLE";
                }

                slotViews.add(new SlotView(
                        slot.getStartTime(),
                        slot.getEndTime(),
                        status,
                        booked != null ? booked.getMemberId() : 0,
                        booked != null ? booked.getAppointmentId() : 0
                ));
            }

            boolean isWorkingDay = workingDays.stream()
                    .anyMatch(w -> dow.equals(w.getDayOfWeek()));

            week.add(new DayView(date, dayLabel, isWorkingDay, slotViews));
        }

        // Today's appointments — only meaningful when viewing the current week.
        List<PersonalTrainingAppointment> todayAppointments = appointments.stream()
                .filter(a -> today.equals(a.getAppointmentDate())
                        && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                .sorted(Comparator.comparing(PersonalTrainingAppointment::getStartTime))
                .toList();

        // DTO instead of the raw entity — see class-level note.
        model.addAttribute("trainer", DtoMapper.toTrainerDTO(trainer));
        model.addAttribute("week", week);
        model.addAttribute("today", today);
        model.addAttribute("monday", monday);
        model.addAttribute("sunday", monday.plusDays(6));
        model.addAttribute("prevMonday", monday.minusWeeks(1));
        model.addAttribute("nextMonday", monday.plusWeeks(1));
        model.addAttribute("currentMonday", currentMonday);
        model.addAttribute("isCurrentWeek", monday.equals(currentMonday));
        model.addAttribute("todayAppointments", todayAppointments);
        model.addAttribute("memberNames", memberNames);
        return "trainer/dashboard";
    }

    // ===================== APPOINTMENTS =====================

    @GetMapping("/appointments")
    public String appointments(@RequestParam(defaultValue = "upcoming") String filter,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String memberQuery,
                               @RequestParam(required = false) String dateFrom,
                               @RequestParam(required = false) String dateTo,
                               HttpSession session, Model model) {
        Trainer trainer = currentTrainer(session);
        if (trainer == null) return "redirect:/login";

        LocalDate today = LocalDate.now();
        List<PersonalTrainingAppointment> all =
                ptService.getTrainerAppointments(trainer.getTrainerId());

        // Build memberId -> name map first (needed by both display and member-name filter).
        // Lookup goes through MemberService — controller does NOT touch DAOs directly.
        Map<Integer, String> memberNames = new HashMap<>();
        for (PersonalTrainingAppointment appt : all) {
            if (!memberNames.containsKey(appt.getMemberId())) {
                memberNames.put(appt.getMemberId(),
                        memberService.getMemberById(appt.getMemberId())
                                .map(Member::getFullName)
                                .orElse("Member #" + appt.getMemberId()));
            }
        }

        // 1) Tab-level filter (upcoming / past / cancelled)
        List<PersonalTrainingAppointment> filtered = switch (filter) {
            case "past" -> all.stream()
                    .filter(a -> a.getAppointmentDate().isBefore(today)
                            && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                    .sorted(Comparator.comparing(PersonalTrainingAppointment::getAppointmentDate)
                            .reversed()
                            .thenComparing(PersonalTrainingAppointment::getStartTime))
                    .toList();
            case "cancelled" -> all.stream()
                    .filter(a -> "CANCELLED".equalsIgnoreCase(a.getStatus()))
                    .sorted(Comparator.comparing(PersonalTrainingAppointment::getAppointmentDate)
                            .reversed())
                    .toList();
            default -> all.stream()
                    .filter(a -> !a.getAppointmentDate().isBefore(today)
                            && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                    .sorted(Comparator.comparing(PersonalTrainingAppointment::getAppointmentDate)
                            .thenComparing(PersonalTrainingAppointment::getStartTime))
                    .toList();
        };

        // 2) Optional extra filters (A: status, B: member name, C: date range)
        String fStatus = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? "" : status.trim();
        String fQuery  = (memberQuery == null) ? "" : memberQuery.trim().toLowerCase(Locale.ROOT);
        LocalDate fFrom = parseDateOrNull(dateFrom);
        LocalDate fTo   = parseDateOrNull(dateTo);

        if (!fStatus.isEmpty() || !fQuery.isEmpty() || fFrom != null || fTo != null) {
            filtered = filtered.stream()
                    .filter(a -> fStatus.isEmpty() || fStatus.equalsIgnoreCase(a.getStatus()))
                    .filter(a -> {
                        if (fQuery.isEmpty()) return true;
                        String name = memberNames.getOrDefault(a.getMemberId(), "")
                                .toLowerCase(Locale.ROOT);
                        return name.contains(fQuery);
                    })
                    .filter(a -> fFrom == null || !a.getAppointmentDate().isBefore(fFrom))
                    .filter(a -> fTo   == null || !a.getAppointmentDate().isAfter(fTo))
                    .toList();
        }

        // Build view-model rows: each row carries pre-computed eligibility flags
        // (canMarkOutcome, canCancel) so the template doesn't make business decisions.
        List<AppointmentRow> rows = new ArrayList<>();
        for (PersonalTrainingAppointment a : filtered) {
            rows.add(new AppointmentRow(
                    a.getAppointmentId(),
                    a.getAppointmentDate(),
                    a.getStartTime(),
                    a.getEndTime(),
                    a.getStatus(),
                    memberNames.getOrDefault(a.getMemberId(),
                            "Member #" + a.getMemberId()),
                    ptService.canMarkOutcome(a, today),
                    ptService.canTrainerCancel(a)
            ));
        }

        // DTO instead of the raw entity — see class-level note.
        model.addAttribute("trainer", DtoMapper.toTrainerDTO(trainer));
        model.addAttribute("rows", rows);
        model.addAttribute("filter", filter);
        model.addAttribute("statusFilter", fStatus);
        model.addAttribute("memberQuery", memberQuery == null ? "" : memberQuery);
        model.addAttribute("dateFrom", dateFrom == null ? "" : dateFrom);
        model.addAttribute("dateTo",   dateTo   == null ? "" : dateTo);

        // Tab counts use the unfiltered list — they're meta-navigation
        long upcomingCount = all.stream()
                .filter(a -> !a.getAppointmentDate().isBefore(today)
                        && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                .count();
        long pastCount = all.stream()
                .filter(a -> a.getAppointmentDate().isBefore(today)
                        && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                .count();
        long cancelledCount = all.stream()
                .filter(a -> "CANCELLED".equalsIgnoreCase(a.getStatus()))
                .count();
        model.addAttribute("upcomingCount", upcomingCount);
        model.addAttribute("pastCount", pastCount);
        model.addAttribute("cancelledCount", cancelledCount);

        return "trainer/appointments";
    }

    // Parses yyyy-MM-dd; returns null on null/blank/invalid input.
    private static LocalDate parseDateOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim()); }
        catch (Exception ex) { return null; }
    }

    /** Trainer marks an upcoming appointment as completed. */
    @PostMapping("/appointments/{id}/complete")
    public String markComplete(@org.springframework.web.bind.annotation.PathVariable int id,
                               HttpSession session,
                               RedirectAttributes ra) {
        Trainer trainer = currentTrainer(session);
        if (trainer == null) return "redirect:/login";
        try {
            ptService.markCompleted(id);
            ra.addFlashAttribute("success", "Appointment marked as completed.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Failed: " + ex.getMessage());
        }
        return "redirect:/trainer/appointments";
    }

    /** Trainer marks a missed appointment as NO_SHOW. */
    @PostMapping("/appointments/{id}/no-show")
    public String markNoShow(@org.springframework.web.bind.annotation.PathVariable int id,
                             HttpSession session,
                             RedirectAttributes ra) {
        Trainer trainer = currentTrainer(session);
        if (trainer == null) return "redirect:/login";
        try {
            ptService.markNoShow(id);
            ra.addFlashAttribute("success", "Appointment marked as no-show.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Failed: " + ex.getMessage());
        }
        return "redirect:/trainer/appointments";
    }

    /** Trainer cancels an upcoming appointment — member will see this in their PT page. */
    @PostMapping("/appointments/{id}/cancel")
    public String cancelAppointment(@org.springframework.web.bind.annotation.PathVariable int id,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        Trainer trainer = currentTrainer(session);
        if (trainer == null) return "redirect:/login";
        try {
            ptService.cancelAppointment(id);
            ra.addFlashAttribute("success", "Appointment cancelled. The member will be notified.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Failed: " + ex.getMessage());
        }
        return "redirect:/trainer/appointments";
    }

    // ===================== PROFILE =====================

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        Trainer trainer = currentTrainer(session);
        if (trainer == null) return "redirect:/login";
        // DTO instead of the raw entity — see class-level note.
        model.addAttribute("trainer", DtoMapper.toTrainerDTO(trainer));
        return "trainer/profile";
    }

    @PostMapping("/profile/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 HttpSession session,
                                 RedirectAttributes ra) {
        Trainer trainer = currentTrainer(session);
        if (trainer == null) return "redirect:/login";

        // UI-level field match (presentation concern — fast feedback before any service call)
        if (newPassword != null && !newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", "New password and confirmation do not match.");
            return "redirect:/trainer/profile";
        }

        // All business rules (length, current password verification, same-as-old, hashing,
        // persistence, session sync) are enforced inside AuthService.
        try {
            AuthService.ResetResult result =
                    authService.changeTrainerPasswordSelf(trainer, currentPassword, newPassword);
            if (result == AuthService.ResetResult.SAME_AS_OLD) {
                ra.addFlashAttribute("error",
                        "New password must be different from the old one.");
            } else if (result == AuthService.ResetResult.SUCCESS) {
                ra.addFlashAttribute("success", "Password changed successfully.");
            } else {
                ra.addFlashAttribute("error", "Could not change password.");
            }
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Unexpected error: " + ex.getMessage());
        }
        return "redirect:/trainer/profile";
    }

    // ===================== VIEW HELPERS =====================

    /** View record for a single day on the schedule grid. */
    public record DayView(LocalDate date, String label, boolean working, List<SlotView> slots) {}

    /** View record for an individual lesson slot. */
    public record SlotView(LocalTime startTime, LocalTime endTime,
                           String status, int memberId, int appointmentId) {}

    /**
     * View record for a single row on the trainer's appointments page.
     * Eligibility booleans are pre-computed by PTService so the template
     * stays free of any business condition (it only renders, never decides).
     */
    public record AppointmentRow(int appointmentId,
                                 LocalDate appointmentDate,
                                 LocalTime startTime,
                                 LocalTime endTime,
                                 String status,
                                 String memberName,
                                 boolean canMarkOutcome,
                                 boolean canCancel) {}
}