package com.iscms.service.policy;

// Harris-Benedict BMR formula for males.
//     BMR = 88.362 + (13.397 × weight) + (4.799 × height) − (5.677 × age)
public class MaleBmrStrategy implements BmrStrategy {

    @Override public String gender() { return "MALE"; }

    @Override
    public double calculate(double weightKg, double heightCm, int ageYears) {
        return 88.362
                + (13.397 * weightKg)
                + (4.799  * heightCm)
                - (5.677  * ageYears);
    }
}