package com.iscms.web.advice;

import com.iscms.exception.InvalidMembershipException;
import com.iscms.exception.PaymentFailedException;
import com.iscms.exception.ResourceNotFoundException;
import com.iscms.exception.UnauthorizedAccessException;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central exception handler for all controllers.
 *
 * Replaces the scattered try/catch blocks that previously lived in every
 * controller method. Each handler maps an exception type to a friendly
 * Thymeleaf error page with an appropriate HTTP status and a safe message.
 *
 * Note: validation errors that the user can fix on the form (e.g. blank name,
 * bad date format) are still handled inline with BindingResult + flash
 * attributes — those don't reach here. This advice catches *page-level*
 * failures: missing resources, business-rule violations, unexpected errors.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // Logger is necessary because we never echo raw stack traces to the user
    // (would leak internal paths). Instead we log the full trace server-side
    // and show only the exception message in the view.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 — resource lookup failed (member, membership, event, trainer, etc.)
    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleNotFound(ResourceNotFoundException ex,
                                 HttpServletRequest req,
                                 Model model) {
        log.warn("ResourceNotFound on {}: {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("statusText", "Not Found");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    // 400 — domain rule violation (e.g. freeze an already-frozen membership)
    @ExceptionHandler({InvalidMembershipException.class, IllegalStateException.class})
    public String handleInvalidState(RuntimeException ex,
                                     HttpServletRequest req,
                                     Model model) {
        log.warn("Invalid state on {}: {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("statusText", "Action Not Allowed");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    // 400 — bad input that slipped past form validation
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleBadArgument(IllegalArgumentException ex,
                                    HttpServletRequest req,
                                    Model model) {
        log.warn("Bad argument on {}: {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("statusText", "Invalid Request");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    // 402 — payment rejected by the mock processor
    @ExceptionHandler(PaymentFailedException.class)
    public String handlePaymentFailure(PaymentFailedException ex,
                                       HttpServletRequest req,
                                       Model model) {
        log.warn("Payment failed on {} ({}): {}",
                req.getRequestURI(), ex.getReasonCode(), ex.getMessage());
        model.addAttribute("status", 402);
        model.addAttribute("statusText", "Payment Failed");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    // 403 — role mismatch or missing session caught mid-flow
    @ExceptionHandler(UnauthorizedAccessException.class)
    public String handleUnauthorized(UnauthorizedAccessException ex,
                                     HttpServletRequest req,
                                     Model model) {
        log.warn("Unauthorized on {}: {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", HttpStatus.FORBIDDEN.value());
        model.addAttribute("statusText", "Access Denied");
        model.addAttribute("message", ex.getMessage());
        return "error";
    }

    // 404 — either no controller mapped the URL (NoHandlerFoundException)
    // or Spring 6.2's static resource handler picked it up and failed
    // (NoResourceFoundException). Both mean "page does not exist" from
    // the user's perspective, so we route them through the same handler.
    // We log at INFO because 404s are routine, not application errors —
    // logging them as ERROR would clutter the operational view.
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public String handleNoHandler(Exception ex,
                                  HttpServletRequest req,
                                  Model model) {
        log.info("No handler for {}", req.getRequestURI());
        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        model.addAttribute("statusText", "Page Not Found");
        model.addAttribute("message",
                "The page you are looking for does not exist or has been moved.");
        return "error";
    }

    // 500 — catch-all for anything we haven't classified above.
    // We deliberately do NOT show the exception message to the user here,
    // because unknown exceptions may contain SQL fragments, library internals,
    // or other data that's unsafe to expose. Generic message only; full
    // trace goes to the server log for the developer to investigate.
    @ExceptionHandler(Exception.class)
    public String handleGeneral(Exception ex,
                                HttpServletRequest req,
                                Model model) {
        log.error("Unhandled exception on " + req.getRequestURI(), ex);
        model.addAttribute("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        model.addAttribute("statusText", "Something Went Wrong");
        model.addAttribute("message",
                "An unexpected error occurred. Our team has been notified. Please try again later.");
        return "error";
    }
}
