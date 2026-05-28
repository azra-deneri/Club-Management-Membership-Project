package com.iscms.web.controller;


import com.iscms.model.Member;
import com.iscms.service.AuthService;
import com.iscms.service.LoginResult;
import com.iscms.service.MemberService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final AuthService authService;
    private final MemberService memberService;


    public LoginController(AuthService authService,
                           MemberService memberService) {
        this.authService = authService;
        this.memberService = memberService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String role,
                        @RequestParam String identifier,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {

        // For MEMBER we use the FROZEN-tolerant variant so frozen members can
        // sign in and self-unfreeze. MemberController takes care of locking
        // them out of every page except Membership.
        LoginResult result = switch (role) {
            case "MEMBER"           -> authService.loginMemberAllowingFrozen(identifier, password);
            case "MANAGER", "ADMIN" -> authService.loginManager(identifier, password);
            case "TRAINER"          -> authService.loginTrainer(identifier, password);
            default                 -> LoginResult.NOT_FOUND;
        };

        if (result.getStatus() != LoginResult.Status.SUCCESS) {
            model.addAttribute("error", describeError(result, role));
            model.addAttribute("role", role);
            model.addAttribute("identifier", identifier);
            return "login";
        }

        Object user = result.getUser();

        // Role mismatch guard: MANAGER and ADMIN share the same login endpoint
        // (both authenticate against the manager table), so we have to verify
        // that the role the user selected on the form actually matches their
        // account's role in the database. Without this an admin who picks
        // "Manager" on the form would land on the manager dashboard, and vice
        // versa — confusing at best, a security gap at worst.
        if (user instanceof com.iscms.model.Manager m) {
            String actualRole = m.getRole();   // "MANAGER" or "ADMIN" from DB
            if (!actualRole.equals(role)) {
                model.addAttribute("error",
                        "This account is registered as " + actualRole
                                + ". Please select the correct role and try again.");
                model.addAttribute("role", role);
                model.addAttribute("identifier", identifier);
                return "login";
            }
        }

        // PAYMENT_HOLD is applied by AuthService.loginMember if 3+ OVERDUE.
        // Member.status follows membership.status — PASSIVE only when membership
        // contract has expired, not because of unpaid installments.

        session.setAttribute("user", user);
        session.setAttribute("role", role);
        return "redirect:/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private String describeError(LoginResult result, String role) {
        return switch (result.getStatus()) {
            case NOT_FOUND -> "Account not found.";
            case WRONG_PASSWORD -> "ADMIN".equals(role)
                    ? "Incorrect password."
                    : "Incorrect password. " + result.getRemainingTries() + " attempt(s) remaining.";
            case LOCKED              -> "Account locked due to too many failed attempts.";
            case SUSPENDED           -> "Account is suspended.";
            case SUGGEST_RESET       -> "Too many failed attempts. Consider resetting your password.";
            case PENDING             -> "Registration is awaiting manager approval.";
            case REGISTRATION_FAILED -> "Registration was rejected.";
            case FROZEN              -> "Your membership is currently frozen.";
            case PASSIVE             -> "Your membership has expired. Please renew.";
            default                  -> "Login failed.";
        };
    }
}