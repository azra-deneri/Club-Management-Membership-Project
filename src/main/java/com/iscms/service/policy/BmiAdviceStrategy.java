package com.iscms.service.policy;

// Strategy contract for BMI-category-specific health advice.
// Each BMI category (UNDERWEIGHT, NORMAL, OVERWEIGHT, OBESE) produces a
// different set of suggestions, so the advice content is true behavior —
// not just a label. Encapsulating each category's advice in its own
// strategy makes recommendation content easy to update without touching
// MemberService, and easy to test in isolation.
public interface BmiAdviceStrategy {

    // Identifier matching the BMI category string.
    String category();

    // 3-line health advice tailored to this BMI category.
    String[] suggestions();
}