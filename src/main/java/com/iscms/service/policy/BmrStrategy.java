package com.iscms.service.policy;

// Strategy contract for Basal Metabolic Rate (BMR) calculation.
// BMR depends on biological sex because the Harris-Benedict equation uses
// sex-specific coefficients. Encapsulating each formula in its own strategy
// removes the gender-based if/else from MemberService and makes the formulas
// independently testable.
public interface BmrStrategy {

    // Identifier matching the gender string used in DB and DTOs.
    String gender();

    // Computes BMR using the Harris-Benedict equation for this gender.
    // Inputs: weight in kg, height in cm, age in completed years.
    double calculate(double weightKg, double heightCm, int ageYears);
}