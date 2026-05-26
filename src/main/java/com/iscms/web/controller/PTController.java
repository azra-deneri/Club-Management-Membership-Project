package com.iscms.web.controller;

import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.model.PersonalTrainingAppointment;
import com.iscms.model.Trainer;
import com.iscms.model.TrainerLessonSlot;
import com.iscms.model.TrainerWorkingDay;
import com.iscms.service.MemberService;
import com.iscms.service.PTService;
import com.iscms.service.policy.TierPolicy;
import com.iscms.service.policy.TierPolicyRegistry;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Member-facing PT (Personal Training) tab — Swing-style week grid.
 *
 * Layout:
 *   - Top:   trainer dropdown + month-usage badge (e.g. 2/8 sessions used)
 *   - Mid:   week navigation (Prev / "2026-05-25 — 2026-05-31" / Next)
 *   - Grid:  7 day rows. Each row = trainer's lesson slots on that weekday,
 *            tagged AVAILABLE / BOOKED-BY-OTHER / TAKEN-BY-ME / PAST.
 *   - Bottom: my appointments table, filter tabs Upcoming / Past / Cancelled.
 */
@Controller
@RequestMapping("/member/pt")
public class PTController {

    private final PTService ptService;
    private final MemberService memberService;

    public PTController(PTService ptService, MemberService memberService) {
        this.ptService = ptService;
        this.memberService = memberService;
    }

    @GetMapping
    public String index(@RequestParam(name = "trainerId", required = false) Integer trainerId,
                        @RequestParam(name = "weekStart", required = false) String weekStartStr,
                        @RequestParam(name = "filter",    required = false, defaultValue = "upcoming") String filter,
                        HttpSession session,
                        Model model) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        // ----- Trainers dropdown -----
        List<Trainer> trainers = ptService.getActiveTrainers();
        if (trainerId == null && !trainers.isEmpty()) {
            trainerId = trainers.get(0).getTrainerId();   // default to first trainer
        }
        model.addAttribute("trainers", trainers);
        model.addAttribute("selectedTrainerId", trainerId);

        // ----- Week range: Monday of the week containing weekStart (default: this week) -----
        LocalDate today = LocalDate.now();
        LocalDate weekStart;
        if (weekStartStr != null && !weekStartStr.isBlank()) {
            try { weekStart = LocalDate.parse(weekStartStr); }
            catch (Exception ignored) { weekStart = today; }
        } else {
            weekStart = today;
        }
        // Snap to Monday
        weekStart = weekStart.minusDays((weekStart.getDayOfWeek().getValue() - 1));
        LocalDate weekEnd = weekStart.plusDays(6);

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd",   weekEnd);
        model.addAttribute("prevWeek",  weekStart.minusDays(7));
        model.addAttribute("nextWeek",  weekStart.plusDays(7));
        model.addAttribute("today",     today);

        // ----- Build the week grid -----
        List<DayRow> weekRows = new ArrayList<>();
        if (trainerId != null) {
            List<TrainerLessonSlot> allSlots = ptService.getLessonSlots(trainerId);
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStart.plusDays(i);
                DayOfWeek dow = date.getDayOfWeek();
                String dayName = dow.name();

                List<TrainerLessonSlot> daySlots = allSlots.stream()
                        .filter(s -> s.getDayOfWeek() != null
                                && s.getDayOfWeek().equalsIgnoreCase(dayName))
                        // Drop malformed slots (end <= start)
                        .filter(s -> s.getStartTime() != null && s.getEndTime() != null
                                && s.getEndTime().isAfter(s.getStartTime()))
                        .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                        .toList();

                List<SlotView> slotViews = new ArrayList<>();
                for (TrainerLessonSlot s : daySlots) {
                    boolean isPast = date.isBefore(today)
                            || (date.equals(today) && s.getStartTime().isBefore(LocalTime.now()));
                    String slotStatus;
                    if (isPast) {
                        slotStatus = "PAST";
                    } else if (ptService.isSlotTaken(trainerId, date, s.getStartTime(), s.getEndTime())) {
                        // Is it mine? Lambda needs effectively-final references.
                        final int trId = trainerId;
                        final LocalDate dt = date;
                        final LocalTime stStart = s.getStartTime();
                        boolean mine = ptService.getMemberAppointments(sessionUser.getMemberId()).stream()
                                .anyMatch(a -> a.getTrainerId() == trId
                                        && dt.equals(a.getAppointmentDate())
                                        && stStart.equals(a.getStartTime())
                                        && "SCHEDULED".equals(a.getStatus()));
                        slotStatus = mine ? "MINE" : "TAKEN";
                    } else {
                        slotStatus = "AVAILABLE";
                    }
                    slotViews.add(new SlotView(s.getStartTime(), s.getEndTime(), slotStatus));
                }
                weekRows.add(new DayRow(date, dayName, slotViews));
            }
        }
        model.addAttribute("weekRows", weekRows);

        // ----- Tier usage badge: X / Y PT sessions this month -----
        String tierInfo = "—";
        Optional<Membership> activeMs = memberService.getActiveMembership(sessionUser.getMemberId());
        if (activeMs.isPresent()) {
            String tier = activeMs.get().getTier();
            TierPolicy policy = TierPolicyRegistry.forTier(tier);
            int limit = policy.maxPtSessionsPerMonth();
            // Count this calendar month's BOOKED/COMPLETED for this member
            int used = (int) ptService.getMemberAppointments(sessionUser.getMemberId()).stream()
                    .filter(a -> a.getAppointmentDate() != null
                            && a.getAppointmentDate().getYear() == today.getYear()
                            && a.getAppointmentDate().getMonthValue() == today.getMonthValue())
                    .filter(a -> "SCHEDULED".equals(a.getStatus()) || "COMPLETED".equals(a.getStatus()))
                    .count();
            if (!policy.allowsPtSessions()) {
                tierInfo = tier + " — PT sessions not included";
            } else {
                tierInfo = tier + " — " + used + " / " + limit + " sessions this month";
            }
        }
        model.addAttribute("tierInfo", tierInfo);

        // ----- My Appointments bucketing -----
        List<PersonalTrainingAppointment> all =
                ptService.getMemberAppointments(sessionUser.getMemberId());
        List<PersonalTrainingAppointment> upcoming  = new ArrayList<>();
        List<PersonalTrainingAppointment> past      = new ArrayList<>();
        List<PersonalTrainingAppointment> cancelled = new ArrayList<>();
        for (PersonalTrainingAppointment a : all) {
            String st = a.getStatus();
            if ("CANCELLED".equals(st)) { cancelled.add(a); continue; }
            LocalDate d = a.getAppointmentDate();
            if (d == null) { past.add(a); continue; }
            if (d.isBefore(today) || "COMPLETED".equals(st) || "NO_SHOW".equals(st)) {
                past.add(a);
            } else {
                upcoming.add(a);
            }
        }

        // Recently cancelled FUTURE appointments — banner notification on the page.
        // These are appointments the member should be aware of (likely trainer-cancelled,
        // since member-cancelled ones the member already knows about).
        List<PersonalTrainingAppointment> recentlyCancelled = cancelled.stream()
                .filter(a -> a.getAppointmentDate() != null
                        && !a.getAppointmentDate().isBefore(today))
                .sorted(Comparator.comparing(PersonalTrainingAppointment::getAppointmentDate))
                .toList();
        model.addAttribute("recentlyCancelled", recentlyCancelled);

        String activeFilter = filter == null ? "upcoming" : filter.toLowerCase();
        List<PersonalTrainingAppointment> visible;
        switch (activeFilter) {
            case "past"      -> visible = past;
            case "cancelled" -> visible = cancelled;
            default          -> { visible = upcoming; activeFilter = "upcoming"; }
        }

        Map<Integer, String> trainerNames = new HashMap<>();
        for (Trainer t : ptService.getAllTrainers()) {
            trainerNames.put(t.getTrainerId(), t.getFullName());
        }

        model.addAttribute("visibleAppts", visible);
        model.addAttribute("trainerNames", trainerNames);
        model.addAttribute("activeFilter", activeFilter);
        model.addAttribute("countUpcoming",  upcoming.size());
        model.addAttribute("countPast",      past.size());
        model.addAttribute("countCancelled", cancelled.size());

        model.addAttribute("isActive", "ACTIVE".equals(sessionUser.getStatus()));
        model.addAttribute("memberStatus", sessionUser.getStatus());
        return "member/pt";
    }

    @PostMapping("/book")
    public String book(@RequestParam int trainerId,
                       @RequestParam String date,
                       @RequestParam String start,
                       @RequestParam String end,
                       @RequestParam(name = "weekStart", required = false) String weekStart,
                       HttpSession session,
                       RedirectAttributes ra) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        try {
            LocalDate d = LocalDate.parse(date);
            LocalTime s = LocalTime.parse(start);
            LocalTime e = LocalTime.parse(end);
            ptService.bookAppointment(sessionUser.getMemberId(), trainerId, d, s, e);
            ra.addFlashAttribute("success", "Appointment booked successfully.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        String back = "/member/pt?trainerId=" + trainerId;
        if (weekStart != null && !weekStart.isBlank()) back += "&weekStart=" + weekStart;
        return "redirect:" + back;
    }

    @PostMapping("/{appointmentId}/cancel")
    public String cancel(@PathVariable int appointmentId,
                         HttpSession session,
                         RedirectAttributes ra) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        boolean owns = ptService.getMemberAppointments(sessionUser.getMemberId()).stream()
                .anyMatch(a -> a.getAppointmentId() == appointmentId);
        if (!owns) {
            ra.addFlashAttribute("error", "Appointment not found.");
            return "redirect:/member/pt";
        }

        try {
            ptService.cancelAppointment(appointmentId);
            ra.addFlashAttribute("success", "Appointment cancelled.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/member/pt";
    }

    // ---------- View models used only by the template ----------
    public record SlotView(LocalTime start, LocalTime end, String status) { }
    public record DayRow(LocalDate date, String dayOfWeek, List<SlotView> slots) { }
}