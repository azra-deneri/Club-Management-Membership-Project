package com.iscms.service.policy;

// Advice for members whose BMI is between 25.0 and 29.9.
// Focus: increased activity, dietary adjustments, optional professional input.
public class OverweightAdvice implements BmiAdviceStrategy {

    @Override public String category() { return "OVERWEIGHT"; }

    @Override public String[] suggestions() {
        return new String[] {
                "Aim for 150-300 minutes of moderate exercise per week.",
                "Reduce processed foods and sugary drinks.",
                "Consider consulting a nutritionist."
        };
    }
}