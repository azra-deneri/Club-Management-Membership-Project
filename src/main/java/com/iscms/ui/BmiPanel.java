package com.iscms.ui;

import com.iscms.model.Member;
import com.iscms.service.MemberService;

import javax.swing.*;
import java.awt.*;

// Read-only BMI panel shown in the member dashboard BMI tab
// Displays BMI value, category, lifestyle recommendations, and estimated daily calories
// Uses Harris-Benedict formula for calorie calculation via MemberService
public class BmiPanel extends JPanel {

    // The currently logged-in member
    private final Member member;

    // Service instance — no DAOs directly in UI layer
    private final MemberService memberService = new MemberService();

    public BmiPanel(Member member) {
        this.member = member;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));
        initUI();
    }

    // Builds the panel content based on whether weight and height are available
    private void initUI() {

        // If weight or height is missing, prompt member to update profile first
        if (member.getWeight() == null || member.getHeight() == null || member.getHeight() == 0) {
            add(new JLabel(
                    "Please update your weight and height in the Profile tab first.",
                    SwingConstants.CENTER), BorderLayout.CENTER);
            return;
        }

        // Use stored BMI value if available, otherwise calculate it on the fly
        double bmi = (member.getBmiValue() != null && member.getBmiValue() > 0)
                ? member.getBmiValue()
                : memberService.calculateBmi(member.getWeight(), member.getHeight());

        // Get the BMI category label (UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE)
        String category = memberService.getBmiCategory(bmi);

        // BMI info section — weight, height, BMI value, category
        JPanel infoPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        infoPanel.setBorder(BorderFactory.createTitledBorder("BMI Information"));
        infoPanel.add(new JLabel("Weight:"));   infoPanel.add(new JLabel(member.getWeight() + " kg"));
        infoPanel.add(new JLabel("Height:"));   infoPanel.add(new JLabel(member.getHeight() + " cm"));
        infoPanel.add(new JLabel("BMI:"));      infoPanel.add(new JLabel(String.format("%.2f", bmi)));
        infoPanel.add(new JLabel("Category:")); infoPanel.add(new JLabel(category));
        add(infoPanel, BorderLayout.NORTH);

        // Recommendations section — lifestyle suggestions based on BMI category
        JPanel suggestionPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        suggestionPanel.setBorder(BorderFactory.createTitledBorder("Recommendations (read-only)"));

        // Add each suggestion as a bullet point label
        for (String s : memberService.getBmiSuggestions(category))
            suggestionPanel.add(new JLabel("• " + s));

        // Add estimated daily calorie needs if date of birth and gender are available
        if (member.getDateOfBirth() != null && member.getGender() != null) {
            double calories = memberService.calculateDailyCalories(
                    member.getWeight(), member.getHeight(),
                    member.getGender(), member.getDateOfBirth());
            suggestionPanel.add(new JLabel(
                    "Estimated Daily Calories (Harris-Benedict): "
                            + String.format("%.0f kcal", calories)));
        }

        add(suggestionPanel, BorderLayout.CENTER);
    }
}