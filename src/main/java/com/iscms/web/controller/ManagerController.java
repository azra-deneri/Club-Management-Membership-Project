package com.iscms.web.controller;

import com.iscms.web.dto.DtoMapper;
import com.iscms.web.dto.ManagerDTO;
import com.iscms.web.dto.MemberRowDTO;
import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.service.EventService;
import com.iscms.service.MemberService;
import com.iscms.model.Event;
import com.iscms.model.EventRegistration;
import com.iscms.service.EventFactory;
import java.util.Comparator;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import com.iscms.model.PersonalTrainingAppointment;
import com.iscms.model.Trainer;
import com.iscms.model.TrainerLessonSlot;
import com.iscms.model.TrainerWorkingDay;
import com.iscms.service.PTService;
import com.iscms.service.AuthService;
import java.time.LocalTime;
import com.iscms.service.ReportService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manager-facing controller.
 * Mirrors the tabs from the desktop ManagerDashboard:
 *   - Members (list, search, suspend, unlock)
 *   - Add Member (cash registration)
 *   - Requests (registration + tier upgrade approvals)
 *   - Events (event CRUD)
 *   - Trainers & PT
 *   - Reports
 *   - My Profile
 *
 * Each tab gets its own GET endpoint and any POST endpoints needed for actions.
 */
@Controller
@RequestMapping("/manager")
public class ManagerController {

    private final MemberService memberService;
    private final EventService eventService;
    private final PTService ptService;
    private final AuthService authService;
    private final ReportService reportService;

    public ManagerController(MemberService memberService,
                             EventService eventService,
                             PTService ptService,
                             AuthService authService,
                             ReportService reportService) {
        this.memberService = memberService;
        this.eventService = eventService;
        this.ptService = ptService;
        this.authService = authService;
        this.reportService = reportService;
    }

    // ========================================================================
    // Members tab
    // ========================================================================

    @GetMapping("/members")
    public String members(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String tier,
                          HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        // Normalize filters; treat null and "ALL" as no filter.
        String query     = q == null ? "" : q.trim().toLowerCase();
        String fStatus   = (status == null || status.isBlank() || "ALL".equals(status)) ? "" : status;
        String fTier     = (tier == null || tier.isBlank() || "ALL".equals(tier)) ? "" : tier;

        // Row projections are now MemberRowDTO from the dto package — see
        // Week 14 refactor: nested view-model classes that used to live
        // inside the controller now sit alongside the other DTOs.
        List<MemberRowDTO> rows = new ArrayList<>();
        for (Member m : memberService.getAllMembers()) {
            // Name/phone search.
            if (!query.isEmpty()
                    && !m.getFullName().toLowerCase().contains(query)
                    && !(m.getPhone() != null && m.getPhone().contains(query))) {
                continue;
            }
            // Status filter.
            if (!fStatus.isEmpty() && !fStatus.equals(m.getStatus())) continue;

            Optional<Membership> ms = memberService.getActiveMembership(m.getMemberId());
            String memberTier = ms.map(Membership::getTier).orElse("NONE");

            // Tier filter (NONE matches members without an active membership).
            if (!fTier.isEmpty() && !fTier.equals(memberTier)) continue;

            rows.add(DtoMapper.toMemberRowDTO(m, memberTier));
        }
        model.addAttribute("members", rows);
        model.addAttribute("query", query);
        model.addAttribute("selectedStatus", fStatus);
        model.addAttribute("selectedTier", fTier);
        return "manager/members";
    }

    @PostMapping("/members/{id}/suspend")
    public String suspendMember(@org.springframework.web.bind.annotation.PathVariable int id,
                                HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        try {
            memberService.suspendMember(id);
            ra.addFlashAttribute("success", "Member suspended.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/members";
    }

    @PostMapping("/members/{id}/unlock")
    public String unlockMember(@org.springframework.web.bind.annotation.PathVariable int id,
                               HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        try {
            memberService.unlockMember(id);
            ra.addFlashAttribute("success", "Member unlocked.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/members";
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Manager requireManagerSession(HttpSession session) {
        Object user = session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (user instanceof Manager mg && ("MANAGER".equals(role) || "ADMIN".equals(role))) return mg;
        return null;
    }

// ========================================================================
    // Add Member tab — Manager registers a member directly with cash payment.
    // ANNUAL_INSTALLMENT is intentionally NOT offered here (it requires the
    // online payment flow, which only members can initiate via self-register).
    // ========================================================================

    @GetMapping("/add-member")
    public String addMemberForm(HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        // Pre-populate the form bean so the template can two-way bind without
        // null-handling on every field. All values default to empty/defaults.
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AddMemberForm());
        }
        return "manager/add-member";
    }

    @PostMapping("/add-member")
    public String submitAddMember(@org.springframework.web.bind.annotation.ModelAttribute("form") AddMemberForm form,
                                  HttpSession session, RedirectAttributes ra, Model model) {
        Manager mg = requireManagerSession(session);
        if (mg == null) return "redirect:/login";

        // Client-side guard against ANNUAL_INSTALLMENT (the dropdown doesn't
        // offer it, but a tampered request could still send it). Reject hard.
        if ("ANNUAL_INSTALLMENT".equals(form.getPackageType())) {
            model.addAttribute("error",
                    "Annual installment plans cannot be created via cash registration. " +
                            "The member must self-register online.");
            model.addAttribute("form", form);
            return "manager/add-member";
        }

        // Field-level validation — matches the Swing RegisterFrame rules.
        // Service-layer validation (age, duplicate phone/email) runs after this.
        String err = validateAddMemberForm(form);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("form", form);
            return "manager/add-member";
        }

        try {
            Member m = new Member();
            m.setFullName(form.getFullName().trim());
            m.setDateOfBirth(java.time.LocalDate.parse(form.getDob().trim()));
            m.setGender(form.getGender());
            m.setPhone(form.getPhone().trim());
            m.setEmail(form.getEmail().trim());
            m.setPassword(form.getPassword());   // hashed inside the service

            memberService.registerMember(m,
                    form.getTier(), form.getPackageType(), mg.getManagerId());

            ra.addFlashAttribute("success",
                    "Member added: " + m.getFullName() + " (" + form.getTier() + " / "
                            + form.getPackageType() + ").");
            return "redirect:/manager/members";
        } catch (java.time.format.DateTimeParseException ex) {
            model.addAttribute("error", "Date of birth must be in YYYY-MM-DD format.");
            model.addAttribute("form", form);
            return "manager/add-member";
        } catch (Exception ex) {
            // Service-layer messages (duplicate phone, age < 18, etc.) surface here.
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
            return "manager/add-member";
        }
    }

    // Returns the first validation error or null if the form is OK.
    // Service layer still runs validateMember() so we don't duplicate every
    // rule here — only the UI-facing format checks live here.
    private String validateAddMemberForm(AddMemberForm f) {
        if (isBlank(f.getFullName()))   return "Full name is required.";
        if (isBlank(f.getDob()))        return "Date of birth is required.";
        if (isBlank(f.getGender()))     return "Gender is required.";
        if (isBlank(f.getPhone()))      return "Phone number is required.";
        if (isBlank(f.getEmail()))      return "Email is required.";
        if (isBlank(f.getPassword()))   return "Password is required.";
        if (isBlank(f.getTier()))       return "Tier is required.";
        if (isBlank(f.getPackageType()))return "Package is required.";
        if (f.getPassword().length() < 8) return "Password must be at least 8 characters.";
        if (!f.getPhone().matches("\\d{10}")) return "Phone must be exactly 10 digits.";
        return null;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    // Form-backing bean for the Add Member screen.
    // Plain getters/setters (no records) because Thymeleaf form binding needs
    // mutable beans — the same form is re-rendered on validation errors.
    public static class AddMemberForm {
        private String fullName = "";
        private String dob      = "";
        private String gender   = "MALE";
        private String phone    = "";
        private String email    = "";
        private String password = "";
        private String tier     = "CLASSIC";
        private String packageType = "MONTHLY";

        public String getFullName()    { return fullName; }
        public void   setFullName(String v) { this.fullName = v; }
        public String getDob()         { return dob; }
        public void   setDob(String v) { this.dob = v; }
        public String getGender()      { return gender; }
        public void   setGender(String v) { this.gender = v; }
        public String getPhone()       { return phone; }
        public void   setPhone(String v) { this.phone = v; }
        public String getEmail()       { return email; }
        public void   setEmail(String v) { this.email = v; }
        public String getPassword()    { return password; }
        public void   setPassword(String v) { this.password = v; }
        public String getTier()        { return tier; }
        public void   setTier(String v) { this.tier = v; }
        public String getPackageType() { return packageType; }
        public void   setPackageType(String v) { this.packageType = v; }
    }


    // ========================================================================
    // Events — manager CRUD. Business rules live in EventService:
    //   - createEvent: date >= tomorrow, end > start, capacity > 0
    //   - updateEvent: not CANCELLED, date >= tomorrow, end > start
    //   - increaseCapacity: not CANCELLED, >5h before start, new > current
    //   - cancelEvent: cascade-cancels all registrations
    // ========================================================================

    @GetMapping("/events")
    public String events(@RequestParam(required = false) String status,
                         @RequestParam(required = false) String category,
                         HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        // Sweep stale events first — ACTIVE events whose date has passed → EXPIRED.
        eventService.expirePastEvents();

        String fStatus   = normalizeFilter(status);
        String fCategory = normalizeFilter(category);

        List<EventRow> rows = new ArrayList<>();
        for (Event e : eventService.getAllEvents()) {
            if (!fStatus.isEmpty()   && !fStatus.equals(e.getStatus()))   continue;
            if (!fCategory.isEmpty() && !fCategory.equals(e.getCategory())) continue;

            int registered = eventService.countRegistered(e.getEventId());
            rows.add(new EventRow(
                    e.getEventId(),
                    e.getEventName(),
                    e.getCategory(),
                    e.getEventDate().toString(),
                    e.getStartTime() + " – " + e.getEndTime(),
                    e.getLocation(),
                    registered + " / " + e.getCapacity(),
                    e.getStatus()
            ));
        }

        // Sort by date ascending so upcoming events sit on top.
        rows.sort(Comparator.comparing(EventRow::date));

        model.addAttribute("rows", rows);
        model.addAttribute("statusFilter", fStatus);
        model.addAttribute("categoryFilter", fCategory);
        return "manager/events";
    }

    @GetMapping("/events/new")
    public String newEventForm(HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new EventForm());
        }
        model.addAttribute("mode", "create");
        return "manager/event-form";
    }

    @PostMapping("/events")
    public String createEvent(@ModelAttribute("form") EventForm form,
                              HttpSession session, RedirectAttributes ra, Model model) {
        Manager mg = requireManagerSession(session);
        if (mg == null) return "redirect:/login";

        String err = validateEventForm(form);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("form", form);
            model.addAttribute("mode", "create");
            return "manager/event-form";
        }

        try {
            // Factory pattern: EventFactory.createEvent runs EventBuilder.build()
            // which enforces invariants (end > start, capacity > 0).
            Event ev = EventFactory.createEvent(
                    form.getEventName().trim(),
                    form.getCategory(),
                    java.time.LocalDate.parse(form.getEventDate()),
                    java.time.LocalTime.parse(form.getStartTime()),
                    java.time.LocalTime.parse(form.getEndTime()),
                    form.getLocation() == null ? "" : form.getLocation().trim(),
                    form.getCapacity(),
                    form.getDescription() == null ? "" : form.getDescription().trim(),
                    mg.getManagerId()
            );
            // Service-level business rules (date >= tomorrow, etc.) run here.
            eventService.createEvent(ev);

            ra.addFlashAttribute("success",
                    "Event created: " + ev.getEventName() + " (" + ev.getEventDate() + ").");
            return "redirect:/manager/events";
        } catch (java.time.format.DateTimeParseException ex) {
            model.addAttribute("error", "Date/time format is invalid.");
            model.addAttribute("form", form);
            model.addAttribute("mode", "create");
            return "manager/event-form";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("mode", "create");
            return "manager/event-form";
        }
    }

    @GetMapping("/events/{id}")
    public String eventDetail(@PathVariable("id") int id,
                              HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        Event ev = eventService.getEventById(id)
                .orElse(null);
        if (ev == null) {
            model.addAttribute("error", "Event not found.");
            return "redirect:/manager/events";
        }

        // Build participant rows (member name resolved via MemberService).
        List<ParticipantRow> participants = new ArrayList<>();
        for (EventRegistration reg : eventService.getRegistrationsByEvent(id)) {
            String name = memberService.getMemberById(reg.getMemberId())
                    .map(Member::getFullName).orElse("(deleted)");
            participants.add(new ParticipantRow(
                    reg.getMemberId(),
                    name,
                    reg.getStatus() != null ? reg.getStatus() : "REGISTERED"
            ));
        }

        model.addAttribute("ev", ev);
        model.addAttribute("registered", eventService.countRegistered(id));
        model.addAttribute("participants", participants);
        return "manager/event-detail";
    }

    @GetMapping("/events/{id}/edit")
    public String editEventForm(@PathVariable("id") int id,
                                HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        Event ev = eventService.getEventById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event not found."));

        if (!model.containsAttribute("form")) {
            EventForm form = new EventForm();
            form.setEventName(ev.getEventName());
            form.setCategory(ev.getCategory());
            form.setEventDate(ev.getEventDate().toString());
            form.setStartTime(ev.getStartTime().toString());
            form.setEndTime(ev.getEndTime().toString());
            form.setLocation(ev.getLocation());
            form.setCapacity(ev.getCapacity());
            form.setDescription(ev.getDescription());
            model.addAttribute("form", form);
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("eventId", id);
        model.addAttribute("currentCapacity", ev.getCapacity());
        return "manager/event-form";
    }

    @PostMapping("/events/{id}")
    public String updateEvent(@PathVariable("id") int id,
                              @ModelAttribute("form") EventForm form,
                              HttpSession session, RedirectAttributes ra, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        String err = validateEventForm(form);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("form", form);
            model.addAttribute("mode", "edit");
            model.addAttribute("eventId", id);
            return "manager/event-form";
        }

        try {
            eventService.updateEvent(
                    id,
                    form.getEventName().trim(),
                    form.getCategory(),
                    java.time.LocalDate.parse(form.getEventDate()),
                    java.time.LocalTime.parse(form.getStartTime()),
                    java.time.LocalTime.parse(form.getEndTime()),
                    form.getLocation() == null ? "" : form.getLocation().trim(),
                    form.getDescription() == null ? "" : form.getDescription().trim()
            );

            // Capacity is updated through a separate endpoint (increase only) —
            // updateEvent intentionally does not touch capacity.

            ra.addFlashAttribute("success", "Event updated.");
            return "redirect:/manager/events/" + id;
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("mode", "edit");
            model.addAttribute("eventId", id);
            return "manager/event-form";
        }
    }

    @PostMapping("/events/{id}/capacity")
    public String increaseCapacity(@PathVariable("id") int id,
                                   @RequestParam int newCapacity,
                                   HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        try {
            eventService.increaseCapacity(id, newCapacity);
            ra.addFlashAttribute("success", "Capacity increased to " + newCapacity + ".");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/events/" + id;
    }

    @PostMapping("/events/{id}/cancel")
    public String cancelEvent(@PathVariable("id") int id,
                              HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        try {
            eventService.cancelEvent(id);
            ra.addFlashAttribute("success",
                    "Event cancelled. All member registrations were cancelled.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/events";
    }

    // UI-level validation. Service layer also validates, but these checks
    // catch the easy cases before we instantiate the Event.
    private String validateEventForm(EventForm f) {
        if (isBlank(f.getEventName()))  return "Event name is required.";
        if (isBlank(f.getCategory()))   return "Category is required.";
        if (isBlank(f.getEventDate()))  return "Event date is required.";
        if (isBlank(f.getStartTime()))  return "Start time is required.";
        if (isBlank(f.getEndTime()))    return "End time is required.";
        if (f.getCapacity() <= 0)       return "Capacity must be greater than 0.";
        return null;
    }

    // Plain bean for two-way binding (Thymeleaf needs mutable getters/setters).
    public static class EventForm {
        private String eventName   = "";
        private String category    = "FITNESS";
        private String eventDate   = "";
        private String startTime   = "";
        private String endTime     = "";
        private String location    = "";
        private int    capacity    = 10;
        private String description = "";

        public String getEventName() { return eventName; }
        public void setEventName(String v) { this.eventName = v; }
        public String getCategory() { return category; }
        public void setCategory(String v) { this.category = v; }
        public String getEventDate() { return eventDate; }
        public void setEventDate(String v) { this.eventDate = v; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String v) { this.startTime = v; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String v) { this.endTime = v; }
        public String getLocation() { return location; }
        public void setLocation(String v) { this.location = v; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int v) { this.capacity = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
    }

    // ========================================================================
    // Trainers — manager CRUD over trainer accounts.
    // ========================================================================

    @GetMapping("/trainers")
    public String trainers(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String specialty,
                           HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        String fStatus    = normalizeFilter(status);
        String fSpecialty = normalizeFilter(specialty);

        List<TrainerRow> rows = new ArrayList<>();
        for (Trainer t : ptService.getAllTrainers()) {
            String trainerStatus = t.isActive() ? "ACTIVE" : "INACTIVE";
            if (!fStatus.isEmpty()    && !fStatus.equals(trainerStatus))    continue;
            if (!fSpecialty.isEmpty() && !fSpecialty.equals(t.getSpecialty())) continue;

            rows.add(new TrainerRow(
                    t.getTrainerId(),
                    t.getFullName(),
                    t.getUsername(),
                    t.getEmail() != null ? t.getEmail() : "",
                    t.getSpecialty() != null ? t.getSpecialty() : "",
                    trainerStatus,
                    t.isLocked()
            ));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("statusFilter", fStatus);
        model.addAttribute("specialtyFilter", fSpecialty);
        return "manager/trainers";
    }

    @GetMapping("/trainers/new")
    public String newTrainerForm(HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TrainerForm());
        }
        model.addAttribute("mode", "create");
        return "manager/trainer-form";
    }

    @PostMapping("/trainers")
    public String createTrainer(@ModelAttribute("form") TrainerForm form,
                                HttpSession session, RedirectAttributes ra, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        String err = validateTrainerForm(form, true);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("form", form);
            model.addAttribute("mode", "create");
            return "manager/trainer-form";
        }

        try {
            Trainer t = new Trainer();
            t.setFullName(form.getFullName().trim());
            t.setUsername(form.getUsername().trim());
            t.setEmail(form.getEmail() == null ? "" : form.getEmail().trim());
            t.setSpecialty(form.getSpecialty());
            t.setPassword(form.getPassword());   // hashed inside service
            t.setActive(true);
            ptService.addTrainer(t);

            ra.addFlashAttribute("success", "Trainer added: " + t.getFullName() + ".");
            return "redirect:/manager/trainers";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("mode", "create");
            return "manager/trainer-form";
        }
    }

    @GetMapping("/trainers/{id}/edit")
    public String editTrainerForm(@PathVariable("id") int id,
                                  HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        Trainer t = ptService.getAllTrainers().stream()
                .filter(x -> x.getTrainerId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found."));

        if (!model.containsAttribute("form")) {
            TrainerForm form = new TrainerForm();
            form.setFullName(t.getFullName());
            form.setUsername(t.getUsername());
            form.setEmail(t.getEmail());
            form.setSpecialty(t.getSpecialty());
            // password left blank — only set if manager wants to reset it
            model.addAttribute("form", form);
        }
        model.addAttribute("mode", "edit");
        model.addAttribute("trainerId", id);
        return "manager/trainer-form";
    }

    @PostMapping("/trainers/{id}")
    public String updateTrainer(@PathVariable("id") int id,
                                @ModelAttribute("form") TrainerForm form,
                                HttpSession session, RedirectAttributes ra, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        String err = validateTrainerForm(form, false);
        if (err != null) {
            model.addAttribute("error", err);
            model.addAttribute("form", form);
            model.addAttribute("mode", "edit");
            model.addAttribute("trainerId", id);
            return "manager/trainer-form";
        }

        try {
            // Password and username are intentionally NOT editable after creation.
            // Even if a tampered request sends them, we ignore — we re-fetch the
            // current username and pass null for password (service treats null
            // as "leave password unchanged").
            Trainer current = ptService.getAllTrainers().stream()
                    .filter(x -> x.getTrainerId() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Trainer not found."));
            ptService.updateTrainerInfo(id,
                    form.getFullName().trim(),
                    current.getUsername(),       // username is immutable post-creation
                    form.getSpecialty(),
                    null);                        // password unchanged
            ra.addFlashAttribute("success", "Trainer updated.");
            return "redirect:/manager/trainers";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("form", form);
            model.addAttribute("mode", "edit");
            model.addAttribute("trainerId", id);
            return "manager/trainer-form";
        }
    }

    @PostMapping("/trainers/{id}/toggle-active")
    public String toggleTrainerActive(@PathVariable("id") int id,
                                      HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        try {
            // Read current state and flip it.
            Trainer t = ptService.getAllTrainers().stream()
                    .filter(x -> x.getTrainerId() == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Trainer not found."));
            ptService.setTrainerActive(id, !t.isActive());
            ra.addFlashAttribute("success",
                    "Trainer is now " + (t.isActive() ? "INACTIVE" : "ACTIVE") + ".");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/trainers";
    }

    @PostMapping("/trainers/{id}/unlock")
    public String unlockTrainer(@PathVariable("id") int id,
                                HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        try {
            ptService.unlockTrainer(id);
            ra.addFlashAttribute("success", "Trainer unlocked.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/trainers";
    }

    // ----- Working Days -----

    @GetMapping("/trainers/{id}/working-days")
    public String workingDays(@PathVariable("id") int id,
                              HttpSession session, Model model, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        Trainer t = ptService.getAllTrainers().stream()
                .filter(x -> x.getTrainerId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found."));

        // Guard: inactive trainers cannot have their schedule edited.
        if (!t.isActive()) {
            ra.addFlashAttribute("error",
                    "Cannot edit the schedule of an inactive trainer. Activate the trainer first.");
            return "redirect:/manager/trainers";
        }

        // Build 7 rows (one per weekday) — existing rows pre-populate, others blank.
        Map<String, TrainerWorkingDay> existing = new HashMap<>();
        for (TrainerWorkingDay wd : ptService.getWorkingDays(id)) {
            existing.put(wd.getDayOfWeek(), wd);
        }

        List<WorkingDayRow> rows = new ArrayList<>();
        for (String day : DAY_ORDER) {
            TrainerWorkingDay wd = existing.get(day);
            rows.add(new WorkingDayRow(
                    day,
                    wd != null,
                    wd != null && wd.getStartTime() != null ? wd.getStartTime().toString() : "",
                    wd != null && wd.getEndTime()   != null ? wd.getEndTime().toString()   : ""
            ));
        }

        model.addAttribute("trainer", t);
        model.addAttribute("rows", rows);
        return "manager/trainer-working-days";
    }

    @PostMapping("/trainers/{id}/working-days")
    public String saveWorkingDays(@PathVariable("id") int id,
                                  @RequestParam Map<String, String> params,
                                  HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        // Guard: block edits to inactive trainers even via direct POST.
        Trainer t = ptService.getAllTrainers().stream()
                .filter(x -> x.getTrainerId() == id).findFirst()
                .orElse(null);
        if (t == null || !t.isActive()) {
            ra.addFlashAttribute("error",
                    "Cannot edit the schedule of an inactive trainer.");
            return "redirect:/manager/trainers";
        }

        try {
            // Build list from the form params. For each day, look for:
            //   active_MONDAY = on/missing
            //   start_MONDAY  = HH:mm
            //   end_MONDAY    = HH:mm
            List<TrainerWorkingDay> list = new ArrayList<>();
            Set<String> activeDays = new HashSet<>();
            for (String day : DAY_ORDER) {
                if (!"on".equals(params.get("active_" + day))) continue;
                String s = params.get("start_" + day);
                String e = params.get("end_" + day);
                if (s == null || s.isBlank() || e == null || e.isBlank())
                    throw new IllegalArgumentException(
                            day + ": Start and end time are required when active.");
                TrainerWorkingDay wd = new TrainerWorkingDay();
                wd.setTrainerId(id);
                wd.setDayOfWeek(day);
                wd.setStartTime(LocalTime.parse(s));
                wd.setEndTime(LocalTime.parse(e));
                list.add(wd);
                activeDays.add(day);
            }
            ptService.saveWorkingDays(id, list);

            // Cascade: drop lesson slots that fall on a day that is no longer
            // a working day, OR whose time falls outside the new working hours.
            // We rewrite the full slot list filtered to surviving days/times.
            List<TrainerLessonSlot> kept = new ArrayList<>();
            for (TrainerLessonSlot slot : ptService.getLessonSlots(id)) {
                if (!activeDays.contains(slot.getDayOfWeek())) continue;
                TrainerWorkingDay wd = list.stream()
                        .filter(w -> w.getDayOfWeek().equals(slot.getDayOfWeek()))
                        .findFirst().orElse(null);
                if (wd == null) continue;
                if (slot.getStartTime().isBefore(wd.getStartTime())
                        || slot.getEndTime().isAfter(wd.getEndTime())) continue;
                kept.add(slot);
            }
            ptService.saveLessonSlots(id, kept);

            ra.addFlashAttribute("success",
                    "Working days saved. Lesson slots outside the new schedule were removed.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/trainers/" + id + "/working-days";
    }

    // ----- Lesson Slots -----

    @GetMapping("/trainers/{id}/lesson-slots")
    public String lessonSlots(@PathVariable("id") int id,
                              HttpSession session, Model model, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        Trainer t = ptService.getAllTrainers().stream()
                .filter(x -> x.getTrainerId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found."));

        // Guard: inactive trainers cannot have their schedule edited.
        if (!t.isActive()) {
            ra.addFlashAttribute("error",
                    "Cannot edit the schedule of an inactive trainer. Activate the trainer first.");
            return "redirect:/manager/trainers";
        }

        List<LessonSlotRow> rows = new ArrayList<>();
        for (TrainerLessonSlot s : ptService.getLessonSlots(id)) {
            rows.add(new LessonSlotRow(
                    s.getSlotId(),
                    s.getDayOfWeek(),
                    s.getStartTime() != null ? s.getStartTime().toString() : "",
                    s.getEndTime()   != null ? s.getEndTime().toString()   : ""
            ));
        }
        // Sort by day-of-week order then start time for a stable view.
        rows.sort(Comparator
                .comparingInt((LessonSlotRow r) -> DAY_ORDER.indexOf(r.day()))
                .thenComparing(LessonSlotRow::start));

        model.addAttribute("trainer", t);
        model.addAttribute("rows", rows);
        return "manager/trainer-lesson-slots";
    }

    @PostMapping("/trainers/{id}/lesson-slots")
    public String saveLessonSlots(@PathVariable("id") int id,
                                  @RequestParam Map<String, String> params,
                                  HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        // Guard: block edits to inactive trainers even via direct POST.
        Trainer t = ptService.getAllTrainers().stream()
                .filter(x -> x.getTrainerId() == id).findFirst()
                .orElse(null);
        if (t == null || !t.isActive()) {
            ra.addFlashAttribute("error",
                    "Cannot edit the schedule of an inactive trainer.");
            return "redirect:/manager/trainers";
        }

        try {
            // The form posts repeating rows indexed slot_day_0, slot_start_0, slot_end_0, etc.
            // Empty-row tolerance: a row with no values is skipped.
            List<TrainerLessonSlot> list = new ArrayList<>();
            int i = 0;
            while (params.containsKey("slot_day_" + i)) {
                String day = params.get("slot_day_" + i);
                String s   = params.get("slot_start_" + i);
                String e   = params.get("slot_end_" + i);
                i++;
                if ((day == null || day.isBlank())
                        && (s == null || s.isBlank())
                        && (e == null || e.isBlank())) continue;
                if (day == null || day.isBlank())
                    throw new IllegalArgumentException("Row " + i + ": Day is required.");
                if (s == null || s.isBlank() || e == null || e.isBlank())
                    throw new IllegalArgumentException(
                            "Row " + i + ": Start and end time are required.");
                TrainerLessonSlot slot = new TrainerLessonSlot();
                slot.setTrainerId(id);
                slot.setDayOfWeek(day);
                slot.setStartTime(LocalTime.parse(s));
                slot.setEndTime(LocalTime.parse(e));
                list.add(slot);
            }
            ptService.saveLessonSlots(id, list);
            ra.addFlashAttribute("success", "Lesson slots saved.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/trainers/" + id + "/lesson-slots";
    }

    // ========================================================================
    // Appointments — manager view + cancel/complete/no-show actions.
    // ========================================================================

    @GetMapping("/appointments")
    public String appointments(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String trainer,
                               HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        String fStatus  = normalizeFilter(status);
        String fTrainer = normalizeFilter(trainer);

        // Build a memberId -> name map once, used for every row.
        Map<Integer, String> memberNames = new HashMap<>();
        for (Member m : memberService.getAllMembers()) {
            memberNames.put(m.getMemberId(), m.getFullName());
        }

        List<AppointmentRow> rows = new ArrayList<>();
        List<Trainer> allTrainers = ptService.getAllTrainers();

        for (Trainer t : allTrainers) {
            if (!fTrainer.isEmpty() && !fTrainer.equals(String.valueOf(t.getTrainerId())))
                continue;
            for (PersonalTrainingAppointment apt : ptService.getTrainerAppointments(t.getTrainerId())) {
                if (!fStatus.isEmpty() && !fStatus.equals(apt.getStatus())) continue;

                rows.add(new AppointmentRow(
                        apt.getAppointmentId(),
                        memberNames.getOrDefault(apt.getMemberId(), "(deleted)"),
                        t.getFullName(),
                        apt.getAppointmentDate().toString(),
                        apt.getStartTime() + " – " + apt.getEndTime(),
                        apt.getStatus()
                ));
            }
        }

        // Sort: SCHEDULED first, then by date descending.
        rows.sort(Comparator
                .comparingInt((AppointmentRow r) -> "SCHEDULED".equals(r.status()) ? 0 : 1)
                .thenComparing(Comparator.comparing(AppointmentRow::date).reversed()));

        // Trainer dropdown options
        List<TrainerOption> trainerOptions = new ArrayList<>();
        for (Trainer t : allTrainers) {
            trainerOptions.add(new TrainerOption(t.getTrainerId(), t.getFullName()));
        }

        model.addAttribute("rows", rows);
        model.addAttribute("statusFilter", fStatus);
        model.addAttribute("trainerFilter", fTrainer);
        model.addAttribute("trainerOptions", trainerOptions);
        return "manager/appointments";
    }


    // ----- Trainer + appointment helpers -----

    private static final List<String> DAY_ORDER = List.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    // Validates the trainer form. `requirePassword` is true for create, false for edit.
    private String validateTrainerForm(TrainerForm f, boolean requirePassword) {
        if (isBlank(f.getFullName()))  return "Full name is required.";
        if (isBlank(f.getUsername()))  return "Username is required.";
        if (isBlank(f.getSpecialty())) return "Specialty is required.";
        if (requirePassword) {
            if (isBlank(f.getPassword())) return "Password is required.";
            if (f.getPassword().length() < 8) return "Password must be at least 8 characters.";
        } else if (!isBlank(f.getPassword()) && f.getPassword().length() < 8) {
            return "If you set a new password, it must be at least 8 characters.";
        }
        return null;
    }

    // Trainer form bean — plain getters/setters for Thymeleaf binding.
    public static class TrainerForm {
        private String fullName  = "";
        private String username  = "";
        private String email     = "";
        private String specialty = "FITNESS";
        private String password  = "";

        public String getFullName() { return fullName; }
        public void setFullName(String v) { this.fullName = v; }
        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { this.email = v; }
        public String getSpecialty() { return specialty; }
        public void setSpecialty(String v) { this.specialty = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
    }

    // View models
    public record TrainerRow(int id, String name, String username, String email,
                             String specialty, String status, boolean locked) {}
    public record TrainerOption(int id, String name) {}
    public record WorkingDayRow(String day, boolean active, String start, String end) {}
    public record LessonSlotRow(int id, String day, String start, String end) {}
    public record AppointmentRow(int id, String memberName, String trainerName,
                                 String date, String time, String status) {}

    // Display-only rows for the events list and participant list.
    public record EventRow(int id, String name, String category, String date,
                           String time, String location, String registered,
                           String status) {}
    public record ParticipantRow(int memberId, String memberName, String status) {}

// ============================================================
    // MY PROFILE — self-service for the logged-in manager/admin.
    // ============================================================

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        Manager mgr = requireManagerSession(session);
        if (mgr == null) return "redirect:/login";
        // DTO instead of the raw Manager entity — password hash and lockout
        // state can no longer be reached through the view layer (Week 14).
        model.addAttribute("manager", DtoMapper.toManagerDTO(mgr));
        return "manager/profile";
    }

    @PostMapping("/profile/password")
    public String changeMyPassword(@RequestParam("currentPassword") String currentPw,
                                   @RequestParam("newPassword") String newPw,
                                   @RequestParam("confirmPassword") String confirmPw,
                                   HttpSession session,
                                   RedirectAttributes ra) {
        Manager mgr = requireManagerSession(session);
        if (mgr == null) return "redirect:/login";

        if (newPw == null || !newPw.equals(confirmPw)) {
            ra.addFlashAttribute("error", "New password and confirmation do not match.");
            return "redirect:/manager/profile";
        }

        try {
            AuthService.ResetResult result =
                    authService.changeManagerPasswordSelf(mgr, currentPw, newPw);
            if (result == AuthService.ResetResult.SAME_AS_OLD) {
                ra.addFlashAttribute("error",
                        "New password must be different from the current one.");
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
        return "redirect:/manager/profile";
    }


    // ============================================================
    // REPORTS — read-only operational reports for managers.
    // ============================================================

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) String tier,
                          @RequestParam(required = false) String pkg,
                          @RequestParam(required = false) String daysRange,
                          HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        // Sweep stale ACTIVE → PASSIVE before reading reports, so operational
        // views never display members whose membership has already ended.
        memberService.sweepExpiredActiveMembers();

        // Normalize filters
        String fTier      = normalizeFilter(tier);
        String fPackage   = normalizeFilter(pkg);
        String fDaysRange = normalizeFilter(daysRange);

        // Build raw list, then filter in-memory
        List<ReportService.ActiveMemberRow> activeAll = reportService.buildActiveMembersRows();
        List<ReportService.ActiveMemberRow> activeFiltered = new ArrayList<>();
        for (ReportService.ActiveMemberRow r : activeAll) {
            if (!fTier.isEmpty()    && !fTier.equals(r.tier()))            continue;
            if (!fPackage.isEmpty() && !fPackage.equals(r.packageType())) continue;
            if (!fDaysRange.isEmpty()) {
                long d = r.daysLeft();
                boolean keep = switch (fDaysRange) {
                    case "0_30"   -> d >= 0 && d <= 30;
                    case "31_90"  -> d >= 31 && d <= 90;
                    case "90_PLUS"-> d > 90;
                    default        -> true;
                };
                if (!keep) continue;
            }
            activeFiltered.add(r);
        }

        var monthly = reportService.getMonthlyRevenue(12);
        var bmiDist = reportService.buildBmiCategoryDistribution();

        int maxBmiCount = bmiDist.stream().mapToInt(b -> b.count()).max().orElse(1);
        double maxMonthlyTotal = monthly.stream().mapToDouble(m -> m.total()).max().orElse(1);
        if (maxBmiCount == 0)         maxBmiCount = 1;
        if (maxMonthlyTotal == 0)     maxMonthlyTotal = 1;

        model.addAttribute("activeRows",        activeFiltered);
        model.addAttribute("expiringRows",      reportService.buildExpiringSoonRows());
        model.addAttribute("bmiRows",           reportService.buildBmiRows());
        model.addAttribute("bmiDist",           bmiDist);
        model.addAttribute("paymentRows",       reportService.buildPaymentRows());
        model.addAttribute("totalRevenue",      reportService.getTotalPaidRevenue());
        model.addAttribute("monthlyRevenue",    monthly);
        model.addAttribute("expiringThreshold", ReportService.EXPIRING_SOON_DAYS);
        model.addAttribute("maxBmiCount",       maxBmiCount);
        model.addAttribute("maxMonthlyTotal",   maxMonthlyTotal);

        // Echo filter values back so the form remembers them
        model.addAttribute("tierFilter",      fTier);
        model.addAttribute("packageFilter",   fPackage);
        model.addAttribute("daysRangeFilter", fDaysRange);
        model.addAttribute("totalActive",     activeAll.size());

        return "manager/reports";
    }

    // ========================================================================
    // Requests tab — Registration + Tier Upgrade approvals
    // ========================================================================

    @GetMapping("/requests")
    public String requests(
            // Registration request filters (prefixed 'reg' to avoid collision)
            @RequestParam(required = false) String regStatus,
            @RequestParam(required = false) String regType,
            @RequestParam(required = false) String regTier,
            @RequestParam(required = false) String regPackage,
            // Tier upgrade request filters (prefixed 'up')
            @RequestParam(required = false) String upStatus,
            @RequestParam(required = false) String upNewTier,
            HttpSession session, Model model) {
        if (requireManagerSession(session) == null) return "redirect:/login";

        // Auto-expire stale requests before showing the page.
        memberService.expireOldRequests();

        // Normalize filters — null / blank / "ALL" all mean "no filter".
        String fRegStatus  = normalizeFilter(regStatus);
        String fRegType    = normalizeFilter(regType);
        String fRegTier    = normalizeFilter(regTier);
        String fRegPackage = normalizeFilter(regPackage);
        String fUpStatus   = normalizeFilter(upStatus);
        String fUpNewTier  = normalizeFilter(upNewTier);

        // --- Registration requests with filtering ---
        List<RegistrationRow> regRows = new ArrayList<>();
        for (com.iscms.model.RegistrationRequest r : memberService.getAllRegistrations()) {
            String rType = r.getType() != null ? r.getType() : "NEW";

            if (!fRegStatus.isEmpty()  && !fRegStatus.equals(r.getStatus())) continue;
            if (!fRegType.isEmpty()    && !fRegType.equals(rType))           continue;
            if (!fRegTier.isEmpty()    && !fRegTier.equals(r.getTier()))     continue;
            if (!fRegPackage.isEmpty() && !fRegPackage.equals(r.getPackageType())) continue;

            String memberName = memberService.getMemberById(r.getMemberId())
                    .map(Member::getFullName).orElse("(deleted)");
            regRows.add(new RegistrationRow(
                    r.getRequestId(),
                    memberName,
                    rType,
                    r.getTier(),
                    r.getPackageType(),
                    String.format("%.2f TL", r.getAmount()),
                    r.getStatus(),
                    r.getExpiresAt() != null ? r.getExpiresAt().toLocalDate().toString() : "-"
            ));
        }

        // --- Tier upgrade requests with filtering ---
        List<UpgradeRow> upRows = new ArrayList<>();
        for (com.iscms.model.TierUpgradeRequest r : memberService.getAllTierUpgrades()) {
            if (!fUpStatus.isEmpty()  && !fUpStatus.equals(r.getStatus()))  continue;
            if (!fUpNewTier.isEmpty() && !fUpNewTier.equals(r.getNewTier())) continue;

            String memberName = memberService.getMemberById(r.getMemberId())
                    .map(Member::getFullName).orElse("(deleted)");
            upRows.add(new UpgradeRow(
                    r.getRequestId(),
                    memberName,
                    r.getOldTier(),
                    r.getNewTier(),
                    String.format("%.2f TL", r.getUpgradeFee()),
                    r.getStatus(),
                    r.getExpiresAt() != null ? r.getExpiresAt().toLocalDate().toString() : "-"
            ));
        }

        // Total pending counts (UNFILTERED — the pill should show real backlog).
        long pendingRegs = memberService.getAllRegistrations().stream()
                .filter(r -> "PENDING".equals(r.getStatus())).count();
        long pendingUps  = memberService.getAllTierUpgrades().stream()
                .filter(r -> "PENDING".equals(r.getStatus())).count();

        model.addAttribute("registrations", regRows);
        model.addAttribute("upgrades", upRows);
        model.addAttribute("pendingRegs", pendingRegs);
        model.addAttribute("pendingUps", pendingUps);

        // Echo selected filter values so the form remembers them across submits.
        model.addAttribute("regStatus", fRegStatus);
        model.addAttribute("regType", fRegType);
        model.addAttribute("regTier", fRegTier);
        model.addAttribute("regPackage", fRegPackage);
        model.addAttribute("upStatus", fUpStatus);
        model.addAttribute("upNewTier", fUpNewTier);

        return "manager/requests";
    }

    // Treats null, blank, and the literal "ALL" as "no filter".
    private static String normalizeFilter(String s) {
        return (s == null || s.isBlank() || "ALL".equals(s)) ? "" : s;
    }

    @PostMapping("/requests/registration/{id}/approve")
    public String approveRegistration(@org.springframework.web.bind.annotation.PathVariable int id,
                                      HttpSession session, RedirectAttributes ra) {
        Manager mg = requireManagerSession(session);
        if (mg == null) return "redirect:/login";
        try {
            memberService.approveRegistration(id, mg.getManagerId());
            ra.addFlashAttribute("success", "Registration approved.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/requests";
    }

    @PostMapping("/requests/registration/{id}/reject")
    public String rejectRegistration(@org.springframework.web.bind.annotation.PathVariable int id,
                                     HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        try {
            memberService.rejectRegistration(id);
            ra.addFlashAttribute("success", "Registration rejected.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/requests";
    }

    @PostMapping("/requests/upgrade/{id}/approve")
    public String approveUpgrade(@org.springframework.web.bind.annotation.PathVariable int id,
                                 HttpSession session, RedirectAttributes ra) {
        Manager mg = requireManagerSession(session);
        if (mg == null) return "redirect:/login";
        try {
            memberService.approveTierUpgrade(id, mg.getManagerId());
            ra.addFlashAttribute("success", "Tier upgrade approved.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/requests";
    }

    @PostMapping("/requests/upgrade/{id}/reject")
    public String rejectUpgrade(@org.springframework.web.bind.annotation.PathVariable int id,
                                HttpSession session, RedirectAttributes ra) {
        if (requireManagerSession(session) == null) return "redirect:/login";
        try {
            memberService.failTierUpgrade(id);
            ra.addFlashAttribute("success", "Tier upgrade rejected.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/manager/requests";
    }

    // View models
    public record RegistrationRow(int id, String memberName, String type,
                                  String tier, String packageType, String amount,
                                  String status, String expiresDate) {}
    public record UpgradeRow(int id, String memberName, String oldTier, String newTier,
                             String fee, String status, String expiresDate) {

    }
}