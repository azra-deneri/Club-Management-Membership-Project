package com.iscms.service.policy;

// Advice for members whose BMI falls below 18.5.
// Focus: caloric surplus, muscle-building, professional nutrition guidance.
public class UnderweightAdvice implements BmiAdviceStrategy {

    @Override public String category() { return "UNDERWEIGHT"; }

    @Override public String[] suggestions() {
        return new String[] {
                "Increase caloric intake with nutrient-dense foods.",
                "Consider strength training to build muscle mass.",
                "Consult a nutritionist for a personalized meal plan."
        };
    }
}