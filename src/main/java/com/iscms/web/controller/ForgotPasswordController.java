package com.iscms.web.controller;

import com.iscms.service.AuthService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Public password-reset endpoint accessible from the login page.
 *
 * <p>Mirrors the Project-1 {@code ForgotPasswordFrame} (Swing):
 * <ul>
 *   <li>MEMBER role: identifier = phone</li>
 *   <li>MANAGER role (also covers ADMIN accounts): identifier = email</li>
 *   <li>TRAINER role: identifier = username</li>
 * </ul>
 *
 * <p>All reset business rules (min length, same-as-old, not-found, BCrypt
 * hashing) live in {@link AuthService}. This controller is a thin HTTP
 * wiring layer with minimal UI-level validation (confirm match, both
 * fields present) and friendly error rendering.
 */
@Controller
public class ForgotPasswordController {

    private final AuthService authService;

    public ForgotPasswordController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/forgot-password")
    public String form(Model model) {
        // Default the role to MEMBER so the placeholder reads "Phone Number".
        if (!model.containsAttribute("role")) {
            model.addAttribute("role", "MEMBER");
        }
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String submit(@RequestParam("role") String role,
                         @RequestParam("identifier") String identifier,
                         @RequestParam("newPassword") String newPassword,
                         @RequestParam("confirmPassword") String confirmPassword,
                         RedirectAttributes ra,
                         Model model) {

        // UI-level checks. Service enforces length and same-as-old on its own.
        if (identifier == null || identifier.isBlank()
                || newPassword == null || newPassword.isBlank()
                || confirmPassword == null || confirmPassword.isBlank()) {
            return renderError(model, role, identifier, "Please fill in all fields.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return renderError(model, role, identifier, "Passwords do not match.");
        }

        AuthService.ResetResult result;
        String roleLabel;
        try {
            switch (role) {
                case "MEMBER" -> {
                    result = authService.resetMemberPassword(identifier.trim(), newPassword);
                    roleLabel = "Member";
                }
                case "MANAGER" -> {
                    // Same path serves both MANAGER and ADMIN accounts.
                    result = authService.resetManagerPasswordByEmail(identifier.trim(), newPassword);
                    roleLabel = "Manager";
                }
                case "TRAINER" -> {
                    result = authService.resetTrainerPasswordByUsername(identifier.trim(), newPassword);
                    roleLabel = "Trainer";
                }
                default -> {
                    return renderError(model, role, identifier, "Please choose a valid role.");
                }
            }
        } catch (IllegalArgumentException ex) {
            // Service throws IAE for length violations and other rule failures.
            return renderError(model, role, identifier, ex.getMessage());
        } catch (Exception ex) {
            return renderError(model, role, identifier, "Unexpected error: " + ex.getMessage());
        }

        switch (result) {
            case SUCCESS -> {
                ra.addFlashAttribute("success",
                        "Password reset successful. You can now log in with your new password.");
                return "redirect:/login";
            }
            case SAME_AS_OLD ->
            { return renderError(model, role, identifier,
                    "New password cannot be the same as your current password."); }
            case NOT_FOUND ->
            { return renderError(model, role, identifier,
                    roleLabel + " account not found."); }
        }
        return renderError(model, role, identifier, "Could not reset password.");
    }

    private String renderError(Model model, String role, String identifier, String error) {
        model.addAttribute("error", error);
        model.addAttribute("role", role);
        model.addAttribute("identifier", identifier);
        return "forgot-password";
    }
}