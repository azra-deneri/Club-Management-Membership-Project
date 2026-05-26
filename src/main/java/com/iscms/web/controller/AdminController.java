package com.iscms.web.controller;

import com.iscms.model.Manager;
import com.iscms.service.AuthService;
import com.iscms.service.ManagerService;
import com.iscms.service.ReportService;
import com.iscms.web.dto.DtoMapper;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin-facing endpoints — separate from {@code ManagerController}.
 *
 * <p>Mirrors the Project-1 {@code AdminDashboard} (Swing) feature set:
 * <ul>
 *   <li>List, lock/unlock, and delete manager accounts</li>
 *   <li>Add new manager (MANAGER or ADMIN role)</li>
 *   <li>View all payments across the system</li>
 *   <li>Self-service password change for the admin account</li>
 * </ul>
 *
 * <p>All business rules live in the service layer
 * ({@code ManagerService}, {@code AuthService}). This controller is a thin
 * HTTP wiring layer and a session-guard.
 *
 * <p>Week 14 refactor: every endpoint that previously wrote a raw Manager
 * entity (or a list of them) to the model now writes ManagerDTOs instead.
 * The {@code /admin/managers} listing is the most critical case — without
 * the DTO it would render every manager's BCrypt password hash into the
 * page source. With the DTO, the password field doesn't exist on the
 * view-model at all, so {@code ${manager.password}} resolves to empty
 * for every manager in the list.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ManagerService managerService;
    private final AuthService authService;
    private final ReportService reportService;

    public AdminController(ManagerService managerService,
                           AuthService authService,
                           ReportService reportService) {
        this.managerService = managerService;
        this.authService = authService;
        this.reportService = reportService;
    }

    /** Session guard — returns the logged-in ADMIN or null. */
    private Manager currentAdmin(HttpSession session) {
        Object user = session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (user instanceof Manager m && "ADMIN".equals(role)) {
            return m;
        }
        return null;
    }

    // ===================== DASHBOARD HUB =====================

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";
        model.addAttribute("admin", DtoMapper.toManagerDTO(admin));
        return "admin/dashboard";
    }

    // ===================== MANAGERS — list + lock + delete =====================

    @GetMapping("/managers")
    public String managers(HttpSession session, Model model) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";
        // Critical refactor: the manager list previously exposed every manager's
        // BCrypt password hash to the view. DtoMapper.toManagerDTOs strips them
        // out — the password field doesn't exist on ManagerDTO at all.
        model.addAttribute("admin",    DtoMapper.toManagerDTO(admin));
        model.addAttribute("managers", DtoMapper.toManagerDTOs(managerService.getAllManagers()));
        return "admin/managers";
    }

    @PostMapping("/managers/{id}/lock-toggle")
    public String toggleLock(@PathVariable("id") int id,
                             HttpSession session,
                             RedirectAttributes ra) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";

        // Guard: admin cannot lock their own account out from this page.
        if (admin.getManagerId() == id) {
            ra.addFlashAttribute("error", "You cannot lock or unlock your own account here.");
            return "redirect:/admin/managers";
        }

        try {
            // Read current state then flip — mirrors AdminDashboard.java behavior.
            Manager target = managerService.getAllManagers().stream()
                    .filter(m -> m.getManagerId() == id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Manager not found."));
            managerService.setLockStatus(id, !target.isLocked());
            ra.addFlashAttribute("success",
                    target.isLocked() ? "Manager unlocked." : "Manager locked.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/managers";
    }

    @PostMapping("/managers/{id}/delete")
    public String deleteManager(@PathVariable("id") int id,
                                HttpSession session,
                                RedirectAttributes ra) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";

        // Guard: admin cannot delete themselves.
        if (admin.getManagerId() == id) {
            ra.addFlashAttribute("error", "You cannot delete your own account.");
            return "redirect:/admin/managers";
        }

        try {
            managerService.removeManager(id);
            ra.addFlashAttribute("success", "Manager deleted.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/managers";
    }

    // ===================== MANAGERS — add new =====================

    @GetMapping("/managers/new")
    public String addManagerForm(HttpSession session, Model model) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";
        model.addAttribute("admin", DtoMapper.toManagerDTO(admin));
        return "admin/manager-form";
    }

    @PostMapping("/managers/new")
    public String addManagerSubmit(@RequestParam String fullName,
                                   @RequestParam String username,
                                   @RequestParam String email,
                                   @RequestParam String password,
                                   @RequestParam String role,
                                   HttpSession session,
                                   RedirectAttributes ra,
                                   Model model) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";

        // All business rules live in ManagerService — controller stays thin.
        String error = managerService.validateManagerForRegistration(
                fullName, username, email, password, role);
        if (error != null) {
            // Re-render form with the user's values so they don't retype everything.
            // Note: the password field is intentionally NOT echoed back — making
            // the user retype it on validation failure is the safer default.
            model.addAttribute("admin", DtoMapper.toManagerDTO(admin));
            model.addAttribute("error", error);
            model.addAttribute("fullName", fullName);
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            model.addAttribute("role", role);
            return "admin/manager-form";
        }

        try {
            Manager m = new Manager();
            m.setFullName(fullName.trim());
            m.setUsername(username.trim());
            m.setEmail(email.trim());
            m.setPassword(password);   // ManagerService hashes
            m.setRole(role);
            m.setLocked(false);
            managerService.addManager(m);
            ra.addFlashAttribute("success",
                    "Manager added: " + m.getFullName() + " (" + role + ").");
            return "redirect:/admin/managers";
        } catch (Exception ex) {
            model.addAttribute("admin", DtoMapper.toManagerDTO(admin));
            model.addAttribute("error", "Could not add manager: " + ex.getMessage());
            model.addAttribute("fullName", fullName);
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            model.addAttribute("role", role);
            return "admin/manager-form";
        }
    }

    // ===================== ALL PAYMENTS =====================

    @GetMapping("/payments")
    public String allPayments(
            @RequestParam(name = "type",   required = false, defaultValue = "ALL") String typeFilter,
            @RequestParam(name = "status", required = false, defaultValue = "ALL") String statusFilter,
            HttpSession session, Model model) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";

        // Apply filter buckets to the full payment list. Filtering is a pure view
        // concern here (no business decision), so it lives in the controller — same
        // approach as EventController's `?filter=` and MemberController's bucketing.
        var all = reportService.buildPaymentRows();
        var filtered = all.stream()
                .filter(p -> "ALL".equals(typeFilter)   || typeFilter.equals(p.type()))
                .filter(p -> "ALL".equals(statusFilter) || statusFilter.equals(p.status()))
                .toList();

        model.addAttribute("admin", DtoMapper.toManagerDTO(admin));
        model.addAttribute("payments", filtered);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("typeFilter", typeFilter);
        model.addAttribute("statusFilter", statusFilter);
        return "admin/payments";
    }

    // ===================== MY PROFILE =====================

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";
        model.addAttribute("admin", DtoMapper.toManagerDTO(admin));
        return "admin/profile";
    }

    @PostMapping("/profile/password")
    public String changeMyPassword(@RequestParam("currentPassword") String currentPw,
                                   @RequestParam("newPassword") String newPw,
                                   @RequestParam("confirmPassword") String confirmPw,
                                   HttpSession session,
                                   RedirectAttributes ra) {
        Manager admin = currentAdmin(session);
        if (admin == null) return "redirect:/login";

        if (newPw == null || !newPw.equals(confirmPw)) {
            ra.addFlashAttribute("error", "New password and confirmation do not match.");
            return "redirect:/admin/profile";
        }

        try {
            // Admin lives in the manager table, so reuse changeManagerPasswordSelf —
            // exact same flow as Manager's My Profile. AuthService enforces all rules
            // (current password verification, BCrypt hashing, same-as-old check).
            AuthService.ResetResult result =
                    authService.changeManagerPasswordSelf(admin, currentPw, newPw);
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
        return "redirect:/admin/profile";
    }
}