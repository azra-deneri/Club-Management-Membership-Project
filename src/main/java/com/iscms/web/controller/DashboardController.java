package com.iscms.web.controller;

import com.iscms.dao.MembershipDAO;
import com.iscms.model.Manager;
import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.model.RegistrationRequest;
import com.iscms.model.Trainer;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Controller
public class DashboardController {

    private final MembershipDAO membershipDAO;
    private final com.iscms.service.MemberService memberService;

    public DashboardController(MembershipDAO membershipDAO,
                               com.iscms.service.MemberService memberService) {
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

        // ADMIN role has its own landing page with manager management features.
        // The shared /dashboard view is for MEMBER, MANAGER, and TRAINER only.
        if ("ADMIN".equals(role)) {
            return "redirect:/admin/dashboard";
        }

        String displayName;
        // Defaults for non-member roles so the template's flag checks
        // don't trip on null values.
        boolean isFrozen = false;
        boolean isPassive = false;
        boolean isPaymentDeactivated = false;
        boolean hasInstallments = false;
        boolean paymentHold = false;
        int overdueCount = 0;

        // Member-specific quick-stats for the welcome panel.
        String memberTier = null;
        Long   memberDaysLeft = null;
        String memberMembershipStatus = null;

        // Manager-specific quick-stat.
        long pendingRegistrations = 0;

        switch (user) {
            case Member m -> {
                displayName = m.getFullName();
                isFrozen  = "FROZEN".equals(m.getStatus());
                isPassive = "PASSIVE".equals(m.getStatus());
                paymentHold = "PAYMENT_HOLD".equals(m.getStatus());
                hasInstallments = memberService.hasActiveInstallmentMembership(m.getMemberId())
                        || memberService.countUnpaidInstallments(m.getMemberId()) > 0;
                overdueCount = memberService.getOverdueInstallments(m.getMemberId()).size();

                // Pull active membership for the welcome card's quick stats.
                Optional<Membership> activeMs = memberService.getActiveMembership(m.getMemberId());
                if (activeMs.isPresent()) {
                    Membership ms = activeMs.get();
                    memberTier = ms.getTier();
                    memberMembershipStatus = ms.getStatus();
                    if (ms.getEndDate() != null) {
                        memberDaysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ms.getEndDate());
                    }
                }

                // PASSIVE recovery: if the latest membership's end_date is still in
                // the future, the PASSIVE was triggered by something else (manual
                // SQL edit during testing) — treat as payment-deactivated.
                if (isPassive) {
                    Optional<Membership> latest = membershipDAO.findAllByMemberId(m.getMemberId()).stream()
                            .filter(ms -> ms.getEndDate() != null)
                            .max((a, b) -> a.getEndDate().compareTo(b.getEndDate()));
                    if (latest.isPresent() && !latest.get().getEndDate().isBefore(LocalDate.now())) {
                        isPaymentDeactivated = true;
                    }
                }
            }
            case Manager m -> {
                displayName = m.getFullName();
                // Count PENDING registration requests so the welcome panel
                // can highlight pending work for the manager at a glance.
                pendingRegistrations = memberService.getAllRegistrations().stream()
                        .filter(r -> "PENDING".equals(r.getStatus()))
                        .count();
            }
            case Trainer t -> displayName = t.getFullName();
            default        -> displayName = "User";
        }

        // Time-of-day greeting for the welcome card.
        int hour = LocalTime.now().getHour();
        String greeting = hour < 12 ? "Good morning"
                : hour < 18 ? "Good afternoon"
                : "Good evening";

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("greeting", greeting);
        model.addAttribute("isFrozen", isFrozen);
        model.addAttribute("isPassive", isPassive);
        model.addAttribute("isPaymentDeactivated", isPaymentDeactivated);
        model.addAttribute("hasInstallments", hasInstallments);
        model.addAttribute("paymentHold", paymentHold);
        model.addAttribute("overdueCount", overdueCount);

        // Member quick-stats (null for non-members or members without active membership)
        model.addAttribute("memberTier", memberTier);
        model.addAttribute("memberDaysLeft", memberDaysLeft);
        model.addAttribute("memberMembershipStatus", memberMembershipStatus);

        // Manager quick-stat
        model.addAttribute("pendingRegistrations", pendingRegistrations);

        return "dashboard";
    }
}