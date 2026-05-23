package com.iscms.service.policy;

// Harris-Benedict BMR formula for females.
//     BMR = 447.593 + (9.247 × weight) + (3.098 × height) − (4.330 × age)
public class FemaleBmrStrategy implements BmrStrategy {

    @Override public String gender() { return "FEMALE"; }

    @Override
    public double calculate(double weightKg, double heightCm, int ageYears) {
        return 447.593
                + (9.247 * weightKg)
                + (3.098 * heightCm)
                - (4.330 * ageYears);
    }
}