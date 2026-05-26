package com.iscms.web.controller;

import com.iscms.model.Event;
import com.iscms.model.EventRegistration;
import com.iscms.model.Member;
import com.iscms.service.EventService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Member-facing Events tab. Lists upcoming events and the caller's own
 * registrations bucketed into Upcoming / Past / Cancelled (selected via
 * ?filter=upcoming|past|cancelled, default upcoming).
 *
 * Registration is gated on member.status == ACTIVE (enforced by
 * EventService.registerMember).
 */
@Controller
@RequestMapping("/member/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public String events(@RequestParam(name = "filter", required = false, defaultValue = "upcoming") String filter,
                         HttpSession session,
                         Model model) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        // Sweep past events first so the page shows accurate ACTIVE/EXPIRED.
        eventService.expirePastEvents();

        List<Event> upcoming = eventService.getActiveEvents();
        List<EventRegistration> allMyRegs =
                eventService.getRegistrationsByMember(sessionUser.getMemberId());

        // Hide-register lookup: events the member is currently REGISTERED for.
        Set<Integer> registeredEventIds = new HashSet<>();
        for (EventRegistration r : allMyRegs) {
            if ("REGISTERED".equals(r.getStatus())) {
                registeredEventIds.add(r.getEventId());
            }
        }

        // Capacity counts for the upcoming list.
        Map<Integer, Integer> registeredCounts = new HashMap<>();
        for (Event e : upcoming) {
            registeredCounts.put(e.getEventId(), eventService.countRegistered(e.getEventId()));
        }

        // Hydrate each registration with the matching Event so the template
        // can show name / date / time without inline lookups.
        Map<Integer, Event> eventLookup = new HashMap<>();
        for (EventRegistration r : allMyRegs) {
            if (!eventLookup.containsKey(r.getEventId())) {
                eventService.getEventById(r.getEventId())
                        .ifPresent(ev -> eventLookup.put(ev.getEventId(), ev));
            }
        }

        // Bucket my registrations into Upcoming / Past / Cancelled.
        // - CANCELLED status -> Cancelled bucket
        // - REGISTERED + event in future -> Upcoming bucket
        // - REGISTERED + event past -> Past bucket (the member did attend / was registered for it)
        LocalDate today = LocalDate.now();
        List<EventRegistration> upcomingRegs  = new ArrayList<>();
        List<EventRegistration> pastRegs      = new ArrayList<>();
        List<EventRegistration> cancelledRegs = new ArrayList<>();

        for (EventRegistration r : allMyRegs) {
            if ("CANCELLED".equals(r.getStatus())) {
                cancelledRegs.add(r);
                continue;
            }
            Event ev = eventLookup.get(r.getEventId());
            if (ev == null || ev.getEventDate() == null) {
                pastRegs.add(r);
                continue;
            }
            if (ev.getEventDate().isAfter(today)) {
                upcomingRegs.add(r);
            } else {
                pastRegs.add(r);
            }
        }

        // Pick the visible bucket based on ?filter=.
        String activeFilter = filter == null ? "upcoming" : filter.toLowerCase();
        List<EventRegistration> visibleRegs;
        switch (activeFilter) {
            case "past"      -> visibleRegs = pastRegs;
            case "cancelled" -> visibleRegs = cancelledRegs;
            default          -> { visibleRegs = upcomingRegs; activeFilter = "upcoming"; }
        }

        boolean isActive = "ACTIVE".equals(sessionUser.getStatus());

        model.addAttribute("upcoming", upcoming);
        model.addAttribute("visibleRegs", visibleRegs);
        model.addAttribute("registeredEventIds", registeredEventIds);
        model.addAttribute("registeredCounts", registeredCounts);
        model.addAttribute("eventLookup", eventLookup);
        model.addAttribute("isActive", isActive);
        model.addAttribute("memberStatus", sessionUser.getStatus());
        model.addAttribute("activeFilter", activeFilter);
        model.addAttribute("countUpcomingRegs",  upcomingRegs.size());
        model.addAttribute("countPastRegs",      pastRegs.size());
        model.addAttribute("countCancelledRegs", cancelledRegs.size());
        return "member/events";
    }

    @PostMapping("/{eventId}/register")
    public String register(@PathVariable int eventId,
                           HttpSession session,
                           RedirectAttributes ra) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        try {
            eventService.registerMember(sessionUser.getMemberId(), eventId, sessionUser.getStatus());
            ra.addFlashAttribute("success", "Successfully registered for the event.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/member/events";
    }

    @PostMapping("/{eventId}/cancel")
    public String cancel(@PathVariable int eventId,
                         HttpSession session,
                         RedirectAttributes ra) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        try {
            eventService.cancelRegistration(sessionUser.getMemberId(), eventId);
            ra.addFlashAttribute("success", "Registration cancelled.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/member/events";
    }
}