package com.iscms.web.controller;

import com.iscms.model.Member;
import com.iscms.service.MemberFactory;
import com.iscms.service.MemberService;
import com.iscms.service.MockPaymentProcessor;
import com.iscms.service.PaymentResult;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * Public self-registration endpoint accessible from the login page.
 *
 * <p>Mirrors the Project-1 {@code RegisterFrame} (Swing). Two payment paths:
 * <ul>
 *   <li>CASH → submits a PENDING registration request awaiting manager approval.
 *       Package limited to MONTHLY / ANNUAL_PREPAID — ANNUAL_INSTALLMENT
 *       requires online payment by design.</li>
 *   <li>ONLINE → stashes the candidate Member in the session, redirects to the
 *       mock card form at {@code /register/payment}. On SUCCESS, the member
 *       is activated immediately via {@link MemberService#selfRegisterMember}.
 *       On failure, no account is created and the user can retry or cancel.</li>
 * </ul>
 *
 * <p>All registration business rules (age, phone format, duplicate phone/email,
 * BCrypt hashing, FK-safe insert ordering) live in {@link MemberService}.
 * This controller is a thin HTTP wiring layer that surfaces service-layer
 * exceptions back to the form so the user can correct their input.
 */
@Controller
@RequestMapping("/register")
public class RegisterController {

    // Session attribute names for the online-payment two-step flow.
    private static final String S_MEMBER  = "pendingRegistration.member";
    private static final String S_TIER    = "pendingRegistration.tier";
    private static final String S_PACKAGE = "pendingRegistration.package";
    private static final String S_AMOUNT  = "pendingRegistration.amount";

    private final MemberService          memberService;
    private final MockPaymentProcessor   paymentProcessor = new MockPaymentProcessor();

    // Card expiry dropdowns — months are static, years are computed per-request
    // so the list automatically rolls forward into a new year.
    private static final java.util.List<String> EXPIRY_MONTHS = java.util.List.of(
            "01", "02", "03", "04", "05", "06",
            "07", "08", "09", "10", "11", "12");

    private static java.util.List<Integer> expiryYears() {
        int current = java.time.LocalDate.now().getYear();
        java.util.List<Integer> years = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) years.add(current + i);
        return years;
    }

    public RegisterController(MemberService memberService) {
        this.memberService = memberService;
    }

    // ====================================================================
    // /register — registration form
    // ====================================================================

    @GetMapping
    public String form(Model model) {
        // Defaults so the form starts in a sensible state (CLASSIC + MONTHLY + CASH).
        if (!model.containsAttribute("tier"))          model.addAttribute("tier", "CLASSIC");
        if (!model.containsAttribute("packageType"))   model.addAttribute("packageType", "MONTHLY");
        if (!model.containsAttribute("paymentMethod")) model.addAttribute("paymentMethod", "CASH");
        if (!model.containsAttribute("gender"))        model.addAttribute("gender", "MALE");
        return "register";
    }

    @PostMapping
    public String submit(
            @RequestParam("fullName")            String fullName,
            @RequestParam("dob")                 String dob,
            @RequestParam("gender")              String gender,
            @RequestParam("phone")               String phone,
            @RequestParam("email")               String email,
            @RequestParam("password")            String password,
            @RequestParam("confirmPassword")     String confirmPassword,
            @RequestParam(name = "weight",                required = false) String weight,
            @RequestParam(name = "height",                required = false) String height,
            @RequestParam(name = "emergencyContactName",  required = false) String emergencyContactName,
            @RequestParam(name = "emergencyContactPhone", required = false) String emergencyContactPhone,
            @RequestParam("tier")                String tier,
            @RequestParam("packageType")         String packageType,
            @RequestParam("paymentMethod")       String paymentMethod,
            HttpSession session,
            Model model,
            RedirectAttributes ra) {

        // ---- UI-level checks (required fields, password match) ----
        // Domain rules (age, phone format, duplicate phone/email) are enforced by
        // MemberService.validateMember and surfaced via IllegalArgumentException.

        if (fullName == null || fullName.isBlank()
                || dob == null || dob.isBlank()
                || phone == null || phone.isBlank()
                || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return renderError(model, fullName, dob, gender, phone, email,
                    weight, height, emergencyContactName, emergencyContactPhone,
                    tier, packageType, paymentMethod,
                    "Please fill in all required fields.");
        }

        if (password.length() < 8) {
            return renderError(model, fullName, dob, gender, phone, email,
                    weight, height, emergencyContactName, emergencyContactPhone,
                    tier, packageType, paymentMethod,
                    "Password must be at least 8 characters.");
        }

        if (!password.equals(confirmPassword)) {
            return renderError(model, fullName, dob, gender, phone, email,
                    weight, height, emergencyContactName, emergencyContactPhone,
                    tier, packageType, paymentMethod,
                    "Passwords do not match.");
        }

        // CASH flow doesn't allow ANNUAL_INSTALLMENT — guard server-side too
        // in case the user bypassed the JS that hides the option.
        if ("CASH".equals(paymentMethod) && "ANNUAL_INSTALLMENT".equals(packageType)) {
            return renderError(model, fullName, dob, gender, phone, email,
                    weight, height, emergencyContactName, emergencyContactPhone,
                    tier, packageType, paymentMethod,
                    "Annual installment is available only with online payment.");
        }

        // ---- Build the Member object ----
        Member m;
        try {
            m = MemberFactory.createPendingMember(
                    fullName.trim(),
                    LocalDate.parse(dob),
                    gender,
                    phone.trim(),
                    email.trim(),
                    password);

            if (weight != null && !weight.isBlank())
                m.setWeight(Double.parseDouble(weight.trim()));
            if (height != null && !height.isBlank())
                m.setHeight(Double.parseDouble(height.trim()));
            m.setEmergencyContactName(
                    emergencyContactName == null ? "" : emergencyContactName.trim());
            m.setEmergencyContactPhone(
                    emergencyContactPhone == null ? "" : emergencyContactPhone.trim());
        } catch (Exception ex) {
            return renderError(model, fullName, dob, gender, phone, email,
                    weight, height, emergencyContactName, emergencyContactPhone,
                    tier, packageType, paymentMethod,
                    "Invalid input: " + ex.getMessage());
        }

        // ---- Dispatch by payment method ----
        if ("ONLINE".equals(paymentMethod)) {
            // Pre-validate at the service layer so duplicates / age failures are
            // shown on the form, not on the payment page. We don't insert yet —
            // the actual insert happens after a SUCCESS PaymentResult.
            try {
                memberService.calculateAmount(tier, packageType); // throws on bad tier/pkg
            } catch (Exception ex) {
                return renderError(model, fullName, dob, gender, phone, email,
                        weight, height, emergencyContactName, emergencyContactPhone,
                        tier, packageType, paymentMethod,
                        "Invalid tier or package selection.");
            }
            // Stash for the payment step.
            session.setAttribute(S_MEMBER,  m);
            session.setAttribute(S_TIER,    tier);
            session.setAttribute(S_PACKAGE, packageType);
            session.setAttribute(S_AMOUNT,  memberService.calculateAmount(tier, packageType));
            return "redirect:/register/payment";
        }

        // CASH flow — submit a PENDING registration request for manager approval.
        try {
            memberService.createRegistrationRequest(m, tier, packageType);
            ra.addFlashAttribute("success",
                    "Registration request submitted. A manager will approve it within 3 days. "
                            + "Please pay in cash at the club to complete your membership.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            // Service-level validation: age, phone format, duplicate phone/email, etc.
            return renderError(model, fullName, dob, gender, phone, email,
                    weight, height, emergencyContactName, emergencyContactPhone,
                    tier, packageType, paymentMethod, ex.getMessage());
        } catch (Exception ex) {
            return renderError(model, fullName, dob, gender, phone, email,
                    weight, height, emergencyContactName, emergencyContactPhone,
                    tier, packageType, paymentMethod,
                    "Could not submit registration: " + ex.getMessage());
        }
    }

    // ====================================================================
    // /register/payment — mock card form
    // ====================================================================

    @GetMapping("/payment")
    public String paymentForm(HttpSession session, Model model, RedirectAttributes ra) {
        if (session.getAttribute(S_MEMBER) == null) {
            ra.addFlashAttribute("error",
                    "Your registration session expired. Please start again.");
            return "redirect:/register";
        }
        model.addAttribute("amount",      session.getAttribute(S_AMOUNT));
        model.addAttribute("tier",        session.getAttribute(S_TIER));
        model.addAttribute("packageType", session.getAttribute(S_PACKAGE));
        model.addAttribute("months",      EXPIRY_MONTHS);
        model.addAttribute("years",       expiryYears());
        return "register-payment";
    }
    @PostMapping("/payment")
    public String paymentSubmit(
            @RequestParam("cardNumber")  String cardNumber,
            @RequestParam("cardName")    String cardName,
            @RequestParam("expiryMonth") String expMonth,
            @RequestParam("expiryYear")  String expYear,
            @RequestParam("cvv")         String cvv,
            HttpSession session,
            Model model,
            RedirectAttributes ra) {

        if (session.getAttribute(S_MEMBER) == null) {
            ra.addFlashAttribute("error",
                    "Your registration session expired. Please start again.");
            return "redirect:/register";
        }

        // ---- Format validation (UI-layer concern) ----
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("\\s+", "");
        if (!digits.matches("\\d{16}")) {
            return renderPaymentError(model, session,
                    "Invalid card number. Please enter 16 digits.");
        }
        if (cvv == null || !cvv.matches("\\d{3}")) {
            return renderPaymentError(model, session,
                    "Invalid CVV. Please enter the 3-digit code on the back of your card.");
        }
        if (cardName == null || cardName.isBlank()) {
            return renderPaymentError(model, session,
                    "Please enter the cardholder name as it appears on the card.");
        }
        YearMonth expiryMonth;
        try {
            int month = Integer.parseInt(expMonth);
            int year  = Integer.parseInt(expYear);
            expiryMonth = YearMonth.of(year, month);
        } catch (Exception ex) {
            return renderPaymentError(model, session,
                    "Please select a valid expiry month and year.");
        }

        // ---- Process via MockPaymentProcessor (domain decision) ----
        PaymentResult result = paymentProcessor.process(digits, expiryMonth);

        if (!result.isSuccess()) {
            return renderPaymentError(model, session,
                    MockPaymentProcessor.describe(result) + " Please try again or cancel.");
        }

        // ---- Payment succeeded — activate the member ----
        Member m       = (Member) session.getAttribute(S_MEMBER);
        String tier    = (String) session.getAttribute(S_TIER);
        String pkg     = (String) session.getAttribute(S_PACKAGE);

        try {
            memberService.selfRegisterMember(m, tier, pkg);
        } catch (IllegalArgumentException ex) {
            // Race: someone else registered the same phone/email between the form
            // submission and now. Tell the user, keep session so they can retry.
            return renderPaymentError(model, session,
                    "Payment was processed but registration failed: " + ex.getMessage()
                            + " Please contact the club for assistance.");
        } catch (Exception ex) {
            return renderPaymentError(model, session,
                    "Payment was processed but registration failed: " + ex.getMessage()
                            + " Please contact the club for assistance.");
        }

        // Success — clear session, send the user to the login page.
        clearPendingRegistration(session);
        ra.addFlashAttribute("success",
                "Welcome to Istanbul Sports Club! Your payment was processed and your "
                        + "membership is now active. You can log in with your phone number.");
        return "redirect:/login";
    }

    @PostMapping("/payment/cancel")
    public String paymentCancel(HttpSession session, RedirectAttributes ra) {
        clearPendingRegistration(session);
        ra.addFlashAttribute("error",
                "Payment cancelled. Your account was not created. "
                        + "You can submit the form again whenever you're ready.");
        return "redirect:/register";
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /**
     * Re-renders the registration form with the user's input preserved and an
     * error banner at the top. Password fields are intentionally not preserved
     * — the user must retype them, which is the safer default for form refills.
     */
    private String renderError(Model model,
                               String fullName, String dob, String gender,
                               String phone, String email,
                               String weight, String height,
                               String emergencyContactName, String emergencyContactPhone,
                               String tier, String packageType, String paymentMethod,
                               String error) {
        model.addAttribute("error", error);
        model.addAttribute("fullName", fullName);
        model.addAttribute("dob", dob);
        model.addAttribute("gender", gender);
        model.addAttribute("phone", phone);
        model.addAttribute("email", email);
        model.addAttribute("weight", weight);
        model.addAttribute("height", height);
        model.addAttribute("emergencyContactName", emergencyContactName);
        model.addAttribute("emergencyContactPhone", emergencyContactPhone);
        model.addAttribute("tier", tier);
        model.addAttribute("packageType", packageType);
        model.addAttribute("paymentMethod", paymentMethod);
        return "register";
    }

    private String renderPaymentError(Model model, HttpSession session, String error) {
        model.addAttribute("error",       error);
        model.addAttribute("amount",      session.getAttribute(S_AMOUNT));
        model.addAttribute("tier",        session.getAttribute(S_TIER));
        model.addAttribute("packageType", session.getAttribute(S_PACKAGE));
        model.addAttribute("months",      EXPIRY_MONTHS);
        model.addAttribute("years",       expiryYears());
        return "register-payment";
    }

    private void clearPendingRegistration(HttpSession session) {
        session.removeAttribute(S_MEMBER);
        session.removeAttribute(S_TIER);
        session.removeAttribute(S_PACKAGE);
        session.removeAttribute(S_AMOUNT);
    }
}