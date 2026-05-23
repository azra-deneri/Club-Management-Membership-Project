package com.iscms.service.policy;

import java.util.Map;

// Central lookup that maps a gender string to its BMR strategy.
// Unknown genders fall back to the female formula, which is the more
// conservative (lower-calorie) estimate — safer for diet recommendations
// than over-estimating maintenance calories.
public final class BmrStrategyRegistry {

    private static final BmrStrategy DEFAULT = new FemaleBmrStrategy();

    private static final Map<String, BmrStrategy> STRATEGIES = Map.of(
            "MALE",   new MaleBmrStrategy(),
            "FEMALE", DEFAULT
    );

    private BmrStrategyRegistry() { }

    public static BmrStrategy forGender(String gender) {
        if (gender == null) return DEFAULT;
        return STRATEGIES.getOrDefault(gender, DEFAULT);
    }
}