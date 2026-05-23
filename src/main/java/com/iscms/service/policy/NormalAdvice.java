package com.iscms.service.policy;

// Advice for members whose BMI is between 18.5 and 24.9.
// Focus: maintenance — sustaining current healthy patterns.
public class NormalAdvice implements BmiAdviceStrategy {

    @Override public String category() { return "NORMAL"; }

    @Override public String[] suggestions() {
        return new String[] {
                "Maintain your current healthy lifestyle.",
                "Regular exercise 3-5 times per week is recommended.",
                "Stay hydrated and keep a balanced diet."
        };
    }
}