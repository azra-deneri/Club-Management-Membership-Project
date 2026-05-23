package com.iscms.service.policy;

import java.util.Map;

// Central lookup that maps a BMI category string to its advice strategy.
// Unknown categories fall back to a safe generic message rather than
// throwing — BMI data could be missing or malformed and the panel
// should still render without crashing.
public final class BmiAdviceRegistry {

    private static final BmiAdviceStrategy FALLBACK = new BmiAdviceStrategy() {
        @Override public String category()      { return "UNKNOWN"; }
        @Override public String[] suggestions() {
            return new String[] { "No suggestions available." };
        }
    };

    private static final Map<String, BmiAdviceStrategy> STRATEGIES = Map.of(
            "UNDERWEIGHT", new UnderweightAdvice(),
            "NORMAL",      new NormalAdvice(),
            "OVERWEIGHT",  new OverweightAdvice(),
            "OBESE",       new ObeseAdvice()
    );

    private BmiAdviceRegistry() { }

    public static BmiAdviceStrategy forCategory(String category) {
        if (category == null) return FALLBACK;
        return STRATEGIES.getOrDefault(category, FALLBACK);
    }
}