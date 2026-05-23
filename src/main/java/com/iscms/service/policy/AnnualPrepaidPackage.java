package com.iscms.service.policy;

// ANNUAL_PREPAID package — pay the full year up front with a 15% discount.
// Valid for 365 days. Best value for committed members.
public class AnnualPrepaidPackage implements PackageStrategy {

    private static final double DISCOUNT_FACTOR = 0.85;   // 15% off

    @Override public String packageType() { return "ANNUAL_PREPAID"; }

    @Override public double calculatePrice(double baseMonthlyPrice) {
        return baseMonthlyPrice * 12 * DISCOUNT_FACTOR;
    }

    @Override public int durationInDays() {
        return 365;
    }

    @Override public double dailyRate(double baseMonthlyPrice) {
        return calculatePrice(baseMonthlyPrice) / 365.0;
    }
}