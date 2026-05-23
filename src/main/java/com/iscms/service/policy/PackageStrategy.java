package com.iscms.service.policy;

import java.time.LocalDate;

// Strategy contract for package-specific pricing and duration rules.
// Each concrete strategy encapsulates the rules for a single package type
// (MONTHLY, ANNUAL_PREPAID, ANNUAL_INSTALLMENT), replacing the package-based
// switch blocks in MemberService.calculateAmount, MemberService.calcEndDate,
// and MemberDashboard.perDayRate.
//
// Package strategies are paired with TierPolicy: tier supplies the base
// monthly price, package supplies the modifier (discount / installment markup)
// and the duration.
public interface PackageStrategy {

    // Identifier matching the package_type string used in DB and DTOs.
    String packageType();

    // Computes the price charged for this package given a tier's base monthly
    // price. Semantics vary by package:
    //   MONTHLY            — returns one month's price
    //   ANNUAL_PREPAID     — returns the full annual price (15% discount)
    //   ANNUAL_INSTALLMENT — returns one installment's price (7% markup)
    double calculatePrice(double baseMonthlyPrice);

    // Membership validity in days. Used to compute end_date from start_date.
    int durationInDays();

    // Per-day rate used for refund calculations when a membership is
    // cancelled before its end date. Semantics vary by package:
    //   MONTHLY            — month's price over 30 days
    //   ANNUAL_PREPAID     — full year's price over 365 days
    //   ANNUAL_INSTALLMENT — one installment over 30 days (current month)
    double dailyRate(double baseMonthlyPrice);

    // Convenience — adds durationInDays() to a start date.
    default LocalDate calculateEndDate(LocalDate startDate) {
        return startDate.plusDays(durationInDays());
    }

    // Whether this package type triggers creation of a 12-month installment
// schedule when the membership is first purchased. Only ANNUAL_INSTALLMENT
// returns true; default is false for one-shot payments.
    default boolean requiresInstallmentSchedule() { return false; }
}