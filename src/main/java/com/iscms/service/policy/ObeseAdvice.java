package com.iscms.service.policy;

// Advice for members whose BMI is 30.0 or above.
// Focus: medical consultation first, low-impact activity, sustainable change.
public class ObeseAdvice implements BmiAdviceStrategy {

    @Override public String category() { return "OBESE"; }

    @Override public String[] suggestions() {
        return new String[] {
                "Consult a healthcare professional for a safe weight-loss plan.",
                "Start with low-impact exercises such as walking or swimming.",
                "Focus on gradual, sustainable lifestyle changes."
        };
    }
}