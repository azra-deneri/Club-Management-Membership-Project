package com.iscms.web.controller;

import com.iscms.model.Installment;
import com.iscms.model.Member;
import com.iscms.model.Membership;
import com.iscms.model.Payment;
import com.iscms.service.MemberService;
import com.iscms.service.MockPaymentProcessor;
import com.iscms.service.PaymentResult;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/member")
public class MemberController {

    private final MemberService memberService;
    private final com.iscms.dao.MemberDAO memberDAO;
    // Stateless processor — safe to share across requests
    private final MockPaymentProcessor paymentProcessor = new MockPaymentProcessor();

    public MemberController(MemberService memberService,
                            com.iscms.dao.MemberDAO memberDAO) {
        this.memberService = memberService;
        this.memberDAO = memberDAO;
    }

    // ========================================================================
    // Profile tab
    // ========================================================================

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Optional<Member> opt = memberService.getMemberById(sessionUser.getMemberId());
        if (opt.isEmpty()) {
            session.invalidate();
            return "redirect:/login";
        }

        Member member = opt.get();
        boolean readOnly = "PAYMENT_HOLD".equals(member.getStatus())
                        || "FROZEN".equals(member.getStatus());
        model.addAttribute("member", member);
        model.addAttribute("onPaymentHold", readOnly);
        return "member/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam String phone,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String weight,
                                @RequestParam(required = false) String height,
                                @RequestParam(required = false, name = "emergencyContactName") String ecName,
                                @RequestParam(required = false, name = "emergencyContactPhone") String ecPhone,
                                HttpSession session,
                                RedirectAttributes ra) {

        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Optional<Member> opt = memberService.getMemberById(sessionUser.getMemberId());
        if (opt.isEmpty()) {
            session.invalidate();
            return "redirect:/login";
        }
        Member member = opt.get();

        if ("PAYMENT_HOLD".equals(member.getStatus())
         || "FROZEN".equals(member.getStatus())) {
            ra.addFlashAttribute("error", "Profile editing is disabled while your account is frozen or on payment hold.");
            return "redirect:/member/profile";
        }

        String trimmedPhone = phone == null ? "" : phone.trim();
        if (trimmedPhone.isEmpty()) {
            ra.addFlashAttribute("error", "Phone number cannot be empty.");
            return "redirect:/member/profile";
        }
        if (trimmedPhone.length() != 10 || !trimmedPhone.matches("\\d+")) {
            ra.addFlashAttribute("error", "Phone must be exactly 10 digits.");
            return "redirect:/member/profile";
        }

        try {
            Double weightVal = (weight == null || weight.isBlank()) ? null : Double.parseDouble(weight.trim());
            Double heightVal = (height == null || height.isBlank()) ? null : Double.parseDouble(height.trim());

            memberService.updateMemberProfile(member.getMemberId(),
                    weightVal, heightVal,
                    ecName  != null ? ecName.trim()  : "",
                    ecPhone != null ? ecPhone.trim() : "");

            if (!trimmedPhone.equals(member.getPhone())) {
                memberService.updatePhone(member.getMemberId(), trimmedPhone);
            }

            String trimmedEmail = email != null ? email.trim() : "";
            String currentEmail = member.getEmail() != null ? member.getEmail() : "";
            if (!trimmedEmail.equals(currentEmail)) {
                memberService.updateEmail(member.getMemberId(), trimmedEmail);
            }

            memberService.getMemberById(member.getMemberId())
                    .ifPresent(refreshed -> session.setAttribute("user", refreshed));

            ra.addFlashAttribute("success", "Profile updated successfully.");

        } catch (NumberFormatException nfe) {
            ra.addFlashAttribute("error", "Weight and Height must be valid numbers.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Update failed: " + ex.getMessage());
        }

        return "redirect:/member/profile";
    }

    // ========================================================================
    // Membership tab
    // ========================================================================

    @GetMapping("/membership")
    public String membership(HttpSession session, Model model) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Optional<Member> opt = memberService.getMemberById(sessionUser.getMemberId());
        if (opt.isEmpty()) {
            session.invalidate();
            return "redirect:/login";
        }
        Member member = opt.get();

        Optional<Membership> active = memberService.getActiveMembership(member.getMemberId());
        Membership ms = null;
        if (active.isPresent()) {
            ms = active.get();
        } else {
            List<Membership> history = memberService.getAllMemberships(member.getMemberId());
            if (!history.isEmpty()) {
                ms = history.get(history.size() - 1);
            }
        }

        if (ms != null) {
            long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ms.getEndDate());

            // Eligibility (which actions are allowed) is decided by the service.
            // Controller just wires the pre-computed flags to the template.
            MemberService.MembershipEligibility e =
                    memberService.computeMembershipEligibility(member, ms);

            model.addAttribute("member", member);
            model.addAttribute("membership", ms);
            model.addAttribute("daysLeft", daysLeft);
            model.addAttribute("onPaymentHold",        e.onPaymentHold());
            model.addAttribute("canUpgrade",           e.canUpgrade());
            model.addAttribute("canFreeze",            e.canFreeze());
            model.addAttribute("canUnfreeze",          e.canUnfreeze());
            model.addAttribute("canCancel",            e.canCancel());
            model.addAttribute("cancellationPending",  e.cancellationPending());
            model.addAttribute("showRenew",            e.showRenew());
            return "member/membership";
        }

        model.addAttribute("member", member);
        model.addAttribute("empty", true);
        return "member/membership";
    }

    @GetMapping("/membership/freeze")
    public String freezeForm(HttpSession session, Model model, RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Optional<Member> opt = memberService.getMemberById(sessionUser.getMemberId());
        if (opt.isEmpty()) { session.invalidate(); return "redirect:/login"; }
        Member member = opt.get();

        if ("PAYMENT_HOLD".equals(member.getStatus())) {
            ra.addFlashAttribute("error", "Freeze is unavailable while on Payment Hold.");
            return "redirect:/member/membership";
        }
        if ("FROZEN".equals(member.getStatus())) {
            ra.addFlashAttribute("error", "Your membership is already frozen. Unfreeze it before freezing again.");
            return "redirect:/member/membership";
        }
        Optional<Membership> activeOpt = memberService.getActiveMembership(member.getMemberId());
        if (activeOpt.isEmpty()) {
            ra.addFlashAttribute("error", "No active membership to freeze.");
            return "redirect:/member/membership";
        }
        Membership ms = activeOpt.get();

        int maxFreeze = TierPolicyRegistry.forTier(ms.getTier()).maxFreezePerMonth();
        int remaining = Math.max(0, maxFreeze - ms.getFreezeCount());

        model.addAttribute("membership", ms);
        model.addAttribute("maxFreeze", maxFreeze);
        model.addAttribute("remainingFreezes", remaining);
        return "member/freeze";
    }

    @PostMapping("/membership/freeze")
    public String freezeSubmit(@RequestParam int days,
                               HttpSession session,
                               RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Optional<Member> opt = memberService.getMemberById(sessionUser.getMemberId());
        if (opt.isEmpty()) { session.invalidate(); return "redirect:/login"; }
        Member member = opt.get();

        Optional<Membership> activeOpt = memberService.getActiveMembership(member.getMemberId());
        if (activeOpt.isEmpty()) {
            ra.addFlashAttribute("error", "No active membership to freeze.");
            return "redirect:/member/membership";
        }

        if (days != 7 && days != 14 && days != 30) {
            ra.addFlashAttribute("error", "Please select a valid freeze duration (7, 14, or 30 days).");
            return "redirect:/member/membership/freeze";
        }

        try {
            memberService.freezeMembership(activeOpt.get().getMembershipId(), days);
            memberService.getMemberById(member.getMemberId())
                    .ifPresent(refreshed -> session.setAttribute("user", refreshed));
            ra.addFlashAttribute("success", "Membership frozen for " + days + " days.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/member/membership";
    }

    @PostMapping("/membership/unfreeze")
    public String unfreezeSubmit(HttpSession session, RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Optional<Member> opt = memberService.getMemberById(sessionUser.getMemberId());
        if (opt.isEmpty()) { session.invalidate(); return "redirect:/login"; }
        Member member = opt.get();

        List<Membership> history = memberService.getAllMemberships(member.getMemberId());
        Optional<Membership> frozen = history.stream()
                .filter(m -> "FROZEN".equals(m.getStatus()))
                .reduce((a, b) -> b);

        if (frozen.isEmpty()) {
            ra.addFlashAttribute("error", "No frozen membership found.");
            return "redirect:/member/membership";
        }

        try {
            memberService.unfreezeMembershipPartial(frozen.get().getMembershipId());
            memberService.getMemberById(member.getMemberId())
                    .ifPresent(refreshed -> session.setAttribute("user", refreshed));
            ra.addFlashAttribute("success", "Membership unfrozen. Welcome back.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/member/membership";
    }

    // ----- Tier Upgrade (form + dispatch to cash/online) -----------------

    @GetMapping("/membership/upgrade")
    public String upgradeForm(HttpSession session, Model model, RedirectAttributes ra) {
        UpgradeContext ctx = loadUpgradeContext(session, ra);
        if (ctx.redirect != null) return ctx.redirect;

        Map<String, Double> feeByTier = new LinkedHashMap<>();
        for (String target : ctx.targetTiers) {
            feeByTier.put(target, computeUpgradeFee(ctx.membership, target, ctx.daysLeft));
        }

        model.addAttribute("membership", ctx.membership);
        model.addAttribute("daysLeft", ctx.daysLeft);
        model.addAttribute("targetTiers", ctx.targetTiers);
        model.addAttribute("feeByTier", feeByTier);
        return "member/upgrade";
    }

    @PostMapping("/membership/upgrade")
    public String upgradeSubmit(@RequestParam String newTier,
                                @RequestParam(defaultValue = "CASH") String paymentMethod,
                                HttpSession session,
                                RedirectAttributes ra) {

        UpgradeContext ctx = loadUpgradeContext(session, ra);
        if (ctx.redirect != null) return ctx.redirect;

        if (!ctx.targetTiers.contains(newTier)) {
            ra.addFlashAttribute("error", "Invalid tier selection.");
            return "redirect:/member/membership/upgrade";
        }

        if ("CASH".equals(paymentMethod)) {
            double fee = computeUpgradeFee(ctx.membership, newTier, ctx.daysLeft);
            try {
                memberService.createTierUpgradeRequest(ctx.member.getMemberId(),
                        ctx.membership.getMembershipId(),
                        ctx.membership.getTier(), newTier, fee);
                ra.addFlashAttribute("success", String.format(
                        "Upgrade request submitted. Please pay %.2f TL in cash to the manager within 3 days.",
                        fee));
            } catch (Exception ex) {
                ra.addFlashAttribute("error", ex.getMessage());
            }
            return "redirect:/member/membership";
        }

        if ("ONLINE".equals(paymentMethod)) {
            return "redirect:/member/membership/upgrade/pay?newTier=" + newTier;
        }

        ra.addFlashAttribute("error", "Invalid payment method.");
        return "redirect:/member/membership/upgrade";
    }

    @GetMapping("/membership/upgrade/pay")
    public String upgradePaymentForm(@RequestParam String newTier,
                                     HttpSession session, Model model,
                                     RedirectAttributes ra) {
        UpgradeContext ctx = loadUpgradeContext(session, ra);
        if (ctx.redirect != null) return ctx.redirect;

        if (!ctx.targetTiers.contains(newTier)) {
            ra.addFlashAttribute("error", "Invalid tier selection.");
            return "redirect:/member/membership/upgrade";
        }

        double fee = computeUpgradeFee(ctx.membership, newTier, ctx.daysLeft);
        addPaymentTemplateAttrs(model,
                "Tier Upgrade",
                ctx.membership.getTier() + " → " + newTier,
                fee,
                "/member/membership/upgrade/pay",
                "/member/membership/upgrade",
                Map.of("newTier", newTier));
        return "member/payment";
    }

    @PostMapping("/membership/upgrade/pay")
    public String upgradePaymentSubmit(@RequestParam String newTier,
                                       @RequestParam String cardNumber,
                                       @RequestParam(required = false, name = "cardName") String cardName,
                                       @RequestParam String expiry,
                                       @RequestParam String cvv,
                                       HttpSession session,
                                       RedirectAttributes ra) {

        UpgradeContext ctx = loadUpgradeContext(session, ra);
        if (ctx.redirect != null) return ctx.redirect;

        if (!ctx.targetTiers.contains(newTier)) {
            ra.addFlashAttribute("error", "Invalid tier selection.");
            return "redirect:/member/membership/upgrade";
        }

        PaymentResult result = validateAndProcess(cardNumber, cardName, expiry, cvv, ra,
                "redirect:/member/membership/upgrade/pay?newTier=" + newTier);
        if (result == null) {
            return "redirect:/member/membership/upgrade/pay?newTier=" + newTier;
        }

        double fee = computeUpgradeFee(ctx.membership, newTier, ctx.daysLeft);
        try {
            memberService.selfUpgradeTier(ctx.member.getMemberId(),
                    ctx.membership.getMembershipId(),
                    ctx.membership.getTier(), newTier, fee);
            ra.addFlashAttribute("success", String.format(
                    "Payment successful — your membership is now %s.", newTier));
        } catch (Exception ex) {
            ra.addFlashAttribute("error",
                    "Payment was processed but the upgrade could not be applied: " + ex.getMessage()
                    + " Please contact the club.");
        }
        return "redirect:/member/membership";
    }

    // ========================================================================
    // Payment History tab — read-only list of all past payments
    // ========================================================================

    @GetMapping("/payments")
    public String payments(HttpSession session, Model model) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        List<Payment> payments = memberService.getPaymentsByMember(sessionUser.getMemberId());
        model.addAttribute("payments", payments);
        return "member/payments";
    }

    // ========================================================================
    // Installments tab — list installments + Pay Now flow
    // ========================================================================

    @GetMapping("/installments")
    public String installments(HttpSession session, Model model) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        // Show this tab if:
        //   (a) member has an active ANNUAL_INSTALLMENT membership, OR
        //   (b) member has unpaid installments from a previous membership
        //       that must be cleared (BR-78).
        boolean hasActive = memberService.hasActiveInstallmentMembership(sessionUser.getMemberId());
        int unpaid = memberService.countUnpaidInstallments(sessionUser.getMemberId());
        if (!hasActive && unpaid == 0) {
            return "redirect:/member/membership";
        }

        // Refresh OVERDUE flags before reading so the page reflects today's
        // state even if no other action has triggered the sweep recently.
        memberService.markOverdueInstallments();

        // If active ANNUAL_INSTALLMENT membership exists, show its installments.
        // Otherwise (BR-78 case: PASSIVE member with leftover debt), show
        // every unpaid installment so the member can clear the debt.
        Optional<Membership> activeMs = memberService.getActiveMembership(sessionUser.getMemberId());
        List<Installment> all;
        if (activeMs.isPresent() && hasActive) {
            all = memberService.getInstallmentsForMembership(activeMs.get().getMembershipId());
        } else {
            // BR-78 view: only the unpaid debt from the previous contract.
            all = memberService.getInstallmentsForMember(sessionUser.getMemberId()).stream()
                    .filter(i -> "PENDING".equals(i.getStatus()) || "OVERDUE".equals(i.getStatus()))
                    .toList();
        }

        long paid    = all.stream().filter(i -> "PAID".equals(i.getStatus())).count();
        long pending = all.stream().filter(i -> "PENDING".equals(i.getStatus())).count();
        long overdue = all.stream().filter(i -> "OVERDUE".equals(i.getStatus())).count();

        // Reload member so the PAYMENT_HOLD banner reflects any auto-applied
        // status change from the OVERDUE sweep above.
        memberService.getMemberById(sessionUser.getMemberId())
                .ifPresent(refreshed -> session.setAttribute("user", refreshed));
        Member current = (Member) session.getAttribute("user");

        model.addAttribute("installments", all);
        model.addAttribute("countPaid", paid);
        model.addAttribute("countPending", pending);
        model.addAttribute("countOverdue", overdue);
        // Phase 2: only the earliest unpaid installment is payable (BR-76).
        Integer earliestUnpaidId = memberService.findEarliestUnpaid(sessionUser.getMemberId())
                .map(i -> i.getInstallmentId())
                .orElse(null);
        model.addAttribute("earliestUnpaidId", earliestUnpaidId);
        model.addAttribute("onPaymentHold", "PAYMENT_HOLD".equals(current.getStatus()));
        return "member/installments";
    }

    @GetMapping("/installments/{installmentId}/pay")
    public String installmentPaymentForm(@PathVariable int installmentId,
                                         HttpSession session, Model model,
                                         RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Installment inst = findOwnInstallment(sessionUser, installmentId, ra);
        if (inst == null) return "redirect:/member/installments";

        if ("PAID".equals(inst.getStatus())) {
            ra.addFlashAttribute("error", "This installment has already been paid.");
            return "redirect:/member/installments";
        }

        addPaymentTemplateAttrs(model,
                "Installment Payment",
                "Installment #" + inst.getInstallmentNo() + "/12",
                inst.getAmount().doubleValue(),
                "/member/installments/" + installmentId + "/pay",
                "/member/installments",
                Map.of());
        return "member/payment";
    }

    @PostMapping("/installments/{installmentId}/pay")
    public String installmentPaymentSubmit(@PathVariable int installmentId,
                                           @RequestParam String cardNumber,
                                           @RequestParam(required = false, name = "cardName") String cardName,
                                           @RequestParam String expiry,
                                           @RequestParam String cvv,
                                           HttpSession session,
                                           RedirectAttributes ra) {

        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        Installment inst = findOwnInstallment(sessionUser, installmentId, ra);
        if (inst == null) return "redirect:/member/installments";

        if ("PAID".equals(inst.getStatus())) {
            ra.addFlashAttribute("error", "This installment has already been paid.");
            return "redirect:/member/installments";
        }

        PaymentResult result = validateAndProcess(cardNumber, cardName, expiry, cvv, ra,
                "redirect:/member/installments/" + installmentId + "/pay");
        if (result == null) {
            return "redirect:/member/installments/" + installmentId + "/pay";
        }

        try {
            memberService.payInstallmentInOrder(installmentId);
            // PAYMENT_HOLD may have been cleared by paying down the overdue count.
            // Refresh the session user so the next page reads the up-to-date status.
            memberService.getMemberById(sessionUser.getMemberId())
                    .ifPresent(refreshed -> session.setAttribute("user", refreshed));
            ra.addFlashAttribute("success", String.format(
                    "Payment successful — installment #%d paid.", inst.getInstallmentNo()));
        } catch (Exception ex) {
            ra.addFlashAttribute("error",
                    "Payment was processed but the installment could not be recorded: " + ex.getMessage());
        }
        return "redirect:/member/installments";
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private Member requireMemberSession(HttpSession session) {
        Object user = session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (user instanceof Member m && "MEMBER".equals(role)) return m;
        return null;
    }

    // Looks up an installment and verifies it belongs to the calling member —
    // prevents URL tampering (paying somebody else's installment).
    private Installment findOwnInstallment(Member sessionUser, int installmentId, RedirectAttributes ra) {
        List<Installment> all = memberService.getInstallmentsForMember(sessionUser.getMemberId());
        Optional<Installment> match = all.stream()
                .filter(i -> i.getInstallmentId() == installmentId)
                .findFirst();
        if (match.isEmpty()) {
            ra.addFlashAttribute("error", "Installment not found.");
            return null;
        }
        return match.get();
    }

    // Common validation + processing pipeline for any mock-payment form.
    // Returns SUCCESS for the caller to act on; returns null if there was a
    // problem (a flash error has already been queued in that case).
    private PaymentResult validateAndProcess(String cardNumber, String cardName,
                                             String expiry, String cvv,
                                             RedirectAttributes ra, String redirectOnError) {
        String cardDigits = cardNumber == null ? "" : cardNumber.replaceAll("\\s+", "");
        String cvvDigits  = cvv == null ? "" : cvv.trim();
        String exp        = expiry == null ? "" : expiry.trim();
        String name       = cardName == null ? "" : cardName.trim();

        if (cardDigits.isEmpty() || cvvDigits.isEmpty() || exp.isEmpty() || name.isEmpty()) {
            ra.addFlashAttribute("error", "Please fill in all card fields.");
            return null;
        }
        if (cardDigits.length() != 16 || !cardDigits.matches("\\d+")) {
            ra.addFlashAttribute("error", "Card number must be 16 digits.");
            return null;
        }
        if (cvvDigits.length() != 3 || !cvvDigits.matches("\\d+")) {
            ra.addFlashAttribute("error", "CVV must be exactly 3 digits.");
            return null;
        }
        YearMonth expiryMonth = parseExpiry(exp);
        if (expiryMonth == null) {
            ra.addFlashAttribute("error", "Invalid expiry. Use MM/YY with a valid month (01–12).");
            return null;
        }

        PaymentResult result = paymentProcessor.process(cardDigits, expiryMonth);
        if (result != PaymentResult.SUCCESS) {
            ra.addFlashAttribute("error", MockPaymentProcessor.describe(result));
            return null;
        }
        return result;
    }

    // Populates the model attributes that the generic payment.html template
    // expects. Each call site supplies its own purpose copy, action URL, and
    // hidden form fields needed to round-trip the operation.
    private void addPaymentTemplateAttrs(Model model,
                                         String pageTitle,
                                         String purposeLine,
                                         double amount,
                                         String formAction,
                                         String backUrl,
                                         Map<String, String> hiddenFields) {
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("purposeLine", purposeLine);
        model.addAttribute("amount", amount);
        model.addAttribute("formAction", formAction);
        model.addAttribute("backUrl", backUrl);
        model.addAttribute("hiddenFields", hiddenFields);
        model.addAttribute("cardSuccess",      MockPaymentProcessor.CARD_SUCCESS);
        model.addAttribute("cardInsufficient", MockPaymentProcessor.CARD_INSUFFICIENT);
        model.addAttribute("cardExpired",      MockPaymentProcessor.CARD_EXPIRED);
        model.addAttribute("cardBadCvv",       MockPaymentProcessor.CARD_INVALID_CVV);
    }

    private UpgradeContext loadUpgradeContext(HttpSession session, RedirectAttributes ra) {
        UpgradeContext ctx = new UpgradeContext();

        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) { ctx.redirect = "redirect:/login"; return ctx; }

        Optional<Member> opt = memberService.getMemberById(sessionUser.getMemberId());
        if (opt.isEmpty()) { session.invalidate(); ctx.redirect = "redirect:/login"; return ctx; }
        ctx.member = opt.get();

        if ("PAYMENT_HOLD".equals(ctx.member.getStatus())) {
            ra.addFlashAttribute("error", "Tier upgrade is unavailable while on Payment Hold.");
            ctx.redirect = "redirect:/member/membership";
            return ctx;
        }
        if ("FROZEN".equals(ctx.member.getStatus())) {
            ra.addFlashAttribute("error", "Tier upgrade is unavailable while your membership is frozen. Unfreeze first.");
            ctx.redirect = "redirect:/member/membership";
            return ctx;
        }

        Optional<Membership> activeOpt = memberService.getActiveMembership(ctx.member.getMemberId());
        if (activeOpt.isEmpty()) {
            ra.addFlashAttribute("error", "No active membership to upgrade.");
            ctx.redirect = "redirect:/member/membership";
            return ctx;
        }
        ctx.membership = activeOpt.get();

        ctx.targetTiers = switch (ctx.membership.getTier()) {
            case "CLASSIC" -> List.of("GOLD", "VIP");
            case "GOLD"    -> List.of("VIP");
            default        -> List.of();
        };
        if (ctx.targetTiers.isEmpty()) {
            ra.addFlashAttribute("error", "You are already on the highest tier.");
            ctx.redirect = "redirect:/member/membership";
            return ctx;
        }

        ctx.daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), ctx.membership.getEndDate());
        return ctx;
    }

    private double computeUpgradeFee(Membership ms, String newTier, long daysLeft) {
        double currentDaily = perDayRate(ms.getTier(), ms.getPackageType());
        double newDaily     = perDayRate(newTier,     ms.getPackageType());
        return Math.max(0, (newDaily - currentDaily) * daysLeft);
    }

    private double perDayRate(String tier, String packageType) {
        double monthly = memberService.calculateAmount(tier, "MONTHLY");
        return switch (packageType) {
            case "ANNUAL_PREPAID"     -> (monthly * 12 * 0.85) / 365;
            case "ANNUAL_INSTALLMENT" -> (monthly * 1.07) / 30;
            default                   -> monthly / 30;
        };
    }

    private YearMonth parseExpiry(String input) {
        if (input == null || input.length() != 5 || input.charAt(2) != '/') return null;
        try {
            int month = Integer.parseInt(input.substring(0, 2));
            int year  = Integer.parseInt(input.substring(3));
            if (month < 1 || month > 12) return null;
            return YearMonth.of(2000 + year, month);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static class UpgradeContext {
        Member member;
        Membership membership;
        List<String> targetTiers = List.of();
        long daysLeft;
        String redirect;
    }

    // ========================================================================
    // Membership cancellation (member self-service)
    //
    // Cancel: stamps cancellation_requested_at. The membership stays ACTIVE
    // until end_date — the member keeps full access during that window.
    // After end_date, the existing AuthService flow transitions the member to
    // PASSIVE (renewal eligible for 1 year).
    //
    // Undo: clears the cancellation flag so the membership renews silently.
    // ========================================================================

    @PostMapping("/membership/cancel")
    public String cancelMembership(HttpSession session, RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        try {
            memberService.cancelMembership(sessionUser.getMemberId());
            // Refresh the session user so the page reflects the new flag.
            memberService.getMemberById(sessionUser.getMemberId())
                    .ifPresent(refreshed -> session.setAttribute("user", refreshed));
            ra.addFlashAttribute("success",
                    "Your cancellation request has been recorded. Your membership will end on its current expiry date.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/member/membership";
    }


    @PostMapping("/membership/cancel/undo")
    public String undoCancel(HttpSession session, RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        try {
            memberDAO.clearCancellationRequested(sessionUser.getMemberId());
            memberService.getMemberById(sessionUser.getMemberId())
                    .ifPresent(refreshed -> session.setAttribute("user", refreshed));
            ra.addFlashAttribute("success",
                    "Cancellation withdrawn. Your membership will continue normally.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/member/membership";
    }

    // ========================================================================
    // Membership renewal (member self-service, online payment)
    //
    // Available when the member is in PASSIVE state (their previous membership
    // expired). Picks a new tier+package, pays online, MemberService creates a
    // brand-new ACTIVE membership and clears all PASSIVE/cancellation markers.
    // ========================================================================

    @GetMapping("/membership/renew")
    public String renewForm(HttpSession session, Model model, RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";

        // Renewal only makes sense for PASSIVE members.
        if (!"PASSIVE".equals(sessionUser.getStatus())) {
            return "redirect:/member/membership";
        }

        // BR-78: block renewal while there are unpaid installments from a
        // previous membership. The member must clear that debt first.
        int unpaid = memberService.countUnpaidInstallments(sessionUser.getMemberId());
        if (unpaid > 0) {
            ra.addFlashAttribute("error",
                    "You have " + unpaid + " unpaid installment(s) from your previous membership. "
                            + "Please pay them before renewing.");
            return "redirect:/member/installments";
        }

        // Fee preview map: tier+package -> price.
        java.util.Map<String, Double> feeByCombo = new java.util.LinkedHashMap<>();
        for (String tier : new String[]{"CLASSIC", "GOLD", "VIP"}) {
            for (String pkg : new String[]{"MONTHLY", "ANNUAL_PREPAID", "ANNUAL_INSTALLMENT"}) {
                feeByCombo.put(tier + "|" + pkg, memberService.calculateAmount(tier, pkg));
            }
        }

        model.addAttribute("member", sessionUser);
        model.addAttribute("feeByCombo", feeByCombo);
        return "member/renew";
    }

    @PostMapping("/membership/renew")
    public String renewProceed(@RequestParam String tier,
                               @RequestParam String packageType,
                               HttpSession session,
                               RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";
        if (!"PASSIVE".equals(sessionUser.getStatus())) {
            return "redirect:/member/membership";
        }

        // Validate inputs minimally; calculateAmount will throw if invalid.
        try {
            memberService.calculateAmount(tier, packageType);
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Please choose a valid tier and package.");
            return "redirect:/member/membership/renew";
        }
        return "redirect:/member/membership/renew/pay?tier=" + tier + "&packageType=" + packageType;
    }

    @GetMapping("/membership/renew/pay")
    public String renewPaymentForm(@RequestParam String tier,
                                   @RequestParam String packageType,
                                   HttpSession session,
                                   Model model,
                                   RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";
        if (!"PASSIVE".equals(sessionUser.getStatus())) {
            return "redirect:/member/membership";
        }

        double amount;
        try {
            amount = memberService.calculateAmount(tier, packageType);
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Invalid tier or package.");
            return "redirect:/member/membership/renew";
        }

        java.util.Map<String, String> hidden = new java.util.LinkedHashMap<>();
        hidden.put("tier", tier);
        hidden.put("packageType", packageType);

        addPaymentTemplateAttrs(model,
                "Renew Membership",
                "Renewal: " + tier + " — " + packageType,
                amount,
                "/member/membership/renew/pay",
                "/member/membership/renew",
                hidden);
        return "member/payment";
    }

    @PostMapping("/membership/renew/pay")
    public String renewPayment(@RequestParam String tier,
                               @RequestParam String packageType,
                               @RequestParam String cardNumber,
                               @RequestParam String cardName,
                               @RequestParam String expiry,
                               @RequestParam String cvv,
                               HttpSession session,
                               RedirectAttributes ra) {
        Member sessionUser = requireMemberSession(session);
        if (sessionUser == null) return "redirect:/login";
        if (!"PASSIVE".equals(sessionUser.getStatus())) {
            return "redirect:/member/membership";
        }

        com.iscms.service.PaymentResult result = validateAndProcess(
                cardNumber, cardName, expiry, cvv, ra,
                "redirect:/member/membership/renew/pay?tier=" + tier + "&packageType=" + packageType);
        if (result == null) {
            return "redirect:/member/membership/renew/pay?tier=" + tier + "&packageType=" + packageType;
        }

        try {
            memberService.selfRenewMembership(sessionUser.getMemberId(), tier, packageType);
            memberService.getMemberById(sessionUser.getMemberId())
                    .ifPresent(refreshed -> session.setAttribute("user", refreshed));
            ra.addFlashAttribute("success",
                    "Membership renewed — welcome back! Your new " + tier + " (" + packageType + ") membership is active.");
        } catch (Exception ex) {
            ra.addFlashAttribute("error",
                    "Payment processed but renewal failed: " + ex.getMessage());
            return "redirect:/member/membership/renew/pay?tier=" + tier + "&packageType=" + packageType;
        }
        return "redirect:/member/membership";
    }
}
