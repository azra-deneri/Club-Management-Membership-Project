package com.iscms.service.policy;

// ANNUAL_INSTALLMENT package — 12 monthly installments at a 7% markup.
// Membership valid for 365 days, but each installment is paid monthly.
// Refund / daily rate calculations operate on the current month's installment
// (divided by 30), not the full year — refunds happen within an active month.
public class AnnualInstallmentPackage implements PackageStrategy {

    private static final double INSTALLMENT_MARKUP = 1.07;   // +7%

    @Override public String packageType() { return "ANNUAL_INSTALLMENT"; }

    @Override public double calculatePrice(double baseMonthlyPrice) {
        return baseMonthlyPrice * INSTALLMENT_MARKUP;
    }

    @Override public int durationInDays() {
        return 365;
    }

    @Override public double dailyRate(double baseMonthlyPrice) {
        // Per-day rate within a single month's installment, not the full year
        return calculatePrice(baseMonthlyPrice) / 30.0;
    }

    @Override public boolean requiresInstallmentSchedule() { return true; }
}