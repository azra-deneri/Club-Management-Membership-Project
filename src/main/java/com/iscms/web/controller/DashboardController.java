package com.iscms.web.controller;

import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.iscms.dao.MembershipDAO;
import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.model.Trainer;
import com.iscms.service.MemberService;
import com.iscms.web.dto.DtoMapper;
import com.iscms.web.dto.ManagerDTO;
import com.iscms.web.dto.MemberDTO;
import com.iscms.web.dto.TrainerDTO;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Renders the shared post-login landing page.
 *
 * Routing rules:
 *   - No session         -> /login
 *   - role = ADMIN       -> /admin/dashboard (separate hub)
 *   - role = MEMBER      -> welcome card + member-specific quick stats + nav
 *   - role = MANAGER     -> welcome card + pending request count + nav
 *   - role = TRAINER     -> welcome card + nav
 *
 * Week 14 refactor notes:
 *  - Entity objects are converted to DTOs before reaching the view. The
 *    session still holds the raw Member/Manager/Trainer (needed for password
 *    hash comparisons in other controllers), but this controller never
 *    surfaces the entity itself to Thymeleaf. ${displayName} comes from the
 *    DTO, not from member.getFullName() reachable via ${user.fullName}.
 *  - The previously large switch in dashboard() now delegates to a
 *    populate*Dashboard() helper per role. Each helper has one
 *    responsibility (Single Responsibility Principle, Week 14).
 */
@Controller
public class DashboardController {

    private final MembershipDAO membershipDAO;
    private final MemberService memberService;

    public DashboardController(MembershipDAO membershipDAO,
                               MemberService memberService) {
        this.membershipDAO = membershipDAO;
        this.memberService = memberService;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Object user = session.getAttribute("user");
        String role = (String) session.getAttribute("role");

        if (user == null || role == null) {
            return "redirect:/login";
        }

        // ADMIN role has its own landing page with manager-management tiles.
        // The shared /dashboard view is for MEMBER, MANAGER, and TRAINER only.
        if ("ADMIN".equals(role)) {
            return "redirect:/admin/dashboard";
        }

        model.addAttribute("role", role);
        model.addAttribute("greeting", computeGreeting());

        // Defensive defaults: every flag/stat read by th:if in the template
        // must have a non-null value so expression evaluation never trips.
        // Role-specific helpers below overwrite the relevant subset.
        applyDefaults(model);

        switch (user) {
            case Member m   -> populateMemberDashboard(m, model);
            case Manager mg -> populateManagerDashboard(mg, model);
            case Trainer t  -> populateTrainerDashboard(t, model);
            default         -> { /* keep defaults */ }
        }

        return "dashboard";
    }

    // ========================================================================
    // Role-specific population
    // ========================================================================

    private void populateMemberDashboard(Member member, Model model) {
        // Convert entity to DTO before any field reaches the view.
        // The DTO carries no password hash, no failedAttempts, no isLocked.
        MemberDTO dto = DtoMapper.toMemberDTO(member);
        model.addAttribute("displayName", dto.fullName());

        boolean isFrozen    = "FROZEN".equals(member.getStatus());
        boolean isPassive   = "PASSIVE".equals(member.getStatus());
        boolean paymentHold = "PAYMENT_HOLD".equals(member.getStatus());

        // PASSIVE recovery: a member can land in PASSIVE either because their
        // membership expired naturally or because they missed payments. We
        // distinguish the two by checking whether the latest membership's
        // end_date is still in the future. If yes, it was a manual SQL/admin
        // deactivation during testing — treat as payment-deactivated so the
        // template surfaces the "go pay your installment" banner.
        boolean isPaymentDeactivated = isPassive && hasFutureEndDate(member.getMemberId());

        int overdueCount = memberService.getOverdueInstallments(member.getMemberId()).size();
        boolean hasInstallments =
                memberService.hasActiveInstallmentMembership(member.getMemberId())
                        || memberService.countUnpaidInstallments(member.getMemberId()) > 0;

        // Membership quick-stats: tier, days remaining, status. Null when the
        // member has no active membership — the template's th:if hides those
        // rows automatically.
        String tier = null;
        Long daysLeft = null;
        String membershipStatus = null;
        Optional<Membership> active = memberService.getActiveMembership(member.getMemberId());
        if (active.isPresent()) {
            Membership ms = active.get();
            tier = ms.getTier();
            membershipStatus = ms.getStatus();
            if (ms.getEndDate() != null) {
                daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ms.getEndDate());
            }
        }

        model.addAttribute("isFrozen", isFrozen);
        model.addAttribute("isPassive", isPassive);
        model.addAttribute("paymentHold", paymentHold);
        model.addAttribute("isPaymentDeactivated", isPaymentDeactivated);
        model.addAttribute("hasInstallments", hasInstallments);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("memberTier", tier);
        model.addAttribute("memberDaysLeft", daysLeft);
        model.addAttribute("memberMembershipStatus", membershipStatus);
    }

    private void populateManagerDashboard(Manager manager, Model model) {
        ManagerDTO dto = DtoMapper.toManagerDTO(manager);
        model.addAttribute("displayName", dto.fullName());

        // Count PENDING registration requests so the welcome panel can
        // highlight pending work for the manager at a glance.
        long pendingRegistrations = memberService.getAllRegistrations().stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .count();

        model.addAttribute("pendingRegistrations", pendingRegistrations);
    }

    private void populateTrainerDashboard(Trainer trainer, Model model) {
        TrainerDTO dto = DtoMapper.toTrainerDTO(trainer);
        model.addAttribute("displayName", dto.fullName());
        // Trainer has no extra quick stats on this shared dashboard;
        // the trainer-specific weekly schedule lives at /trainer/dashboard.
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Sets every flag/stat the template might read to a safe default so
     * th:if expressions never hit a null. Role-specific helpers overwrite
     * the relevant subset.
     */
    private void applyDefaults(Model model) {
        model.addAttribute("displayName", "User");
        model.addAttribute("isFrozen", false);
        model.addAttribute("isPassive", false);
        model.addAttribute("isPaymentDeactivated", false);
        model.addAttribute("paymentHold", false);
        model.addAttribute("hasInstallments", false);
        model.addAttribute("overdueCount", 0);
        model.addAttribute("memberTier", null);
        model.addAttribute("memberDaysLeft", null);
        model.addAttribute("memberMembershipStatus", null);
        model.addAttribute("pendingRegistrations", 0L);
    }

    /** True if the member's most recent membership has not yet ended. */
    private boolean hasFutureEndDate(int memberId) {
        return membershipDAO.findAllByMemberId(memberId).stream()
                .filter(ms -> ms.getEndDate() != null)
                .max((a, b) -> a.getEndDate().compareTo(b.getEndDate()))
                .map(latest -> !latest.getEndDate().isBefore(LocalDate.now()))
                .orElse(false);
    }

    /** Time-of-day greeting for the welcome card. */
    private String computeGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }
}