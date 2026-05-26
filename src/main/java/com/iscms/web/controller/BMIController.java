package com.iscms.web.controller;

import com.iscms.model.Member;
import com.iscms.service.MemberService;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Member-facing BMI tab.
 *
 * Flow:
 *   - GET shows the form pre-filled with the member's saved weight/height.
 *   - POST computes BMI + category + advice list + daily calorie target,
 *     all delegated to MemberService (which uses BmiAdviceStrategy and
 *     BmrStrategy under the hood — showcased as the Strategy pattern).
 */
@Controller
@RequestMapping("/member/bmi")
public class BMIController {

    private final MemberService memberService;

    public BMIController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public String form(HttpSession session, Model model) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        // Pre-fill from the member's saved weight/height (may be null).
        model.addAttribute("weight", sessionUser.getWeight());
        model.addAttribute("height", sessionUser.getHeight());
        model.addAttribute("memberGender", sessionUser.getGender());
        model.addAttribute("hasResult", false);
        return "member/bmi";
    }

    @PostMapping("/calculate")
    public String calculate(@RequestParam Double weight,
                            @RequestParam Double height,
                            HttpSession session,
                            Model model) {
        Member sessionUser = (Member) session.getAttribute("user");
        String role = (String) session.getAttribute("role");
        if (sessionUser == null || !"MEMBER".equals(role)) {
            return "redirect:/login";
        }

        // Defensive: invalid input -> show form with error, no result.
        if (weight == null || height == null || weight <= 0 || height <= 0) {
            model.addAttribute("weight", weight);
            model.addAttribute("height", height);
            model.addAttribute("memberGender", sessionUser.getGender());
            model.addAttribute("hasResult", false);
            model.addAttribute("error", "Weight and height must be positive numbers.");
            return "member/bmi";
        }

        // ----- BMI + category -----
        double bmi = memberService.calculateBmi(weight, height);
        String category = memberService.getBmiCategory(bmi);
        String[] suggestions = memberService.getBmiSuggestions(category);

        // ----- Daily calorie target (BmrStrategy: Male/Female) -----
        // Requires gender + DOB. Skip silently if either is missing — we still
        // show BMI + advice, just without the calorie target.
        Double dailyCalories = null;
        try {
            if (sessionUser.getGender() != null && sessionUser.getDateOfBirth() != null) {
                dailyCalories = memberService.calculateDailyCalories(
                        weight, height,
                        sessionUser.getGender(),
                        sessionUser.getDateOfBirth());
            }
        } catch (Exception ignored) {
            dailyCalories = null;
        }

        model.addAttribute("weight", weight);
        model.addAttribute("height", height);
        model.addAttribute("memberGender", sessionUser.getGender());
        model.addAttribute("bmi", bmi);
        model.addAttribute("category", category);
        model.addAttribute("suggestions", suggestions);
        model.addAttribute("dailyCalories", dailyCalories);
        model.addAttribute("hasResult", true);
        return "member/bmi";
    }
}