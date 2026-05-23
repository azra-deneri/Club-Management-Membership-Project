package com.iscms.service.policy;

// MONTHLY package — pay one month's tier price, valid for 30 days.
// No discount, no markup. Most flexible for the member, lowest commitment.
public class MonthlyPackage implements PackageStrategy {

    @Override public String packageType() { return "MONTHLY"; }

    @Override public double calculatePrice(double baseMonthlyPrice) {
        return baseMonthlyPrice;
    }

    @Override public int durationInDays() {
        return 30;
    }

    @Override public double dailyRate(double baseMonthlyPrice) {
        return baseMonthlyPrice / 30.0;
    }
}