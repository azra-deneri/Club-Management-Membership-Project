package com.iscms;

import com.iscms.service.policy.*;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

// Unit tests verifying each PackageStrategy implementation independently
// and the registry's dispatch. Uses 1000.0 as a round base price so
// expected values are easy to read and verify by hand.
public class PackageStrategyTest {

    private static final double BASE = 1000.0;
    private static final double EPS  = 0.001;

    // --- MONTHLY ---

    @Test
    void monthly_returnsBasePriceAnd30DayDuration() {
        PackageStrategy p = new MonthlyPackage();
        assertEquals("MONTHLY", p.packageType());
        assertEquals(BASE, p.calculatePrice(BASE), EPS);
        assertEquals(30, p.durationInDays());
        assertEquals(BASE / 30.0, p.dailyRate(BASE), EPS);
        assertFalse(p.requiresInstallmentSchedule());

    }

    // --- ANNUAL_PREPAID ---

    @Test
    void annualPrepaid_applies15PercentDiscountOverFullYear() {
        PackageStrategy p = new AnnualPrepaidPackage();
        assertEquals("ANNUAL_PREPAID", p.packageType());
        // 1000 * 12 * 0.85 = 10200
        assertEquals(10200.0, p.calculatePrice(BASE), EPS);
        assertEquals(365, p.durationInDays());
        // 10200 / 365
        assertEquals(10200.0 / 365.0, p.dailyRate(BASE), EPS);
        assertFalse(p.requiresInstallmentSchedule());
    }

    // --- ANNUAL_INSTALLMENT ---

    @Test
    void annualInstallment_applies7PercentMarkupPerMonth() {
        PackageStrategy p = new AnnualInstallmentPackage();
        assertEquals("ANNUAL_INSTALLMENT", p.packageType());
        // 1000 * 1.07 = 1070 (per installment, not total)
        assertEquals(1070.0, p.calculatePrice(BASE), EPS);
        assertEquals(365, p.durationInDays());
        // Daily rate is one installment / 30, not annual / 365
        assertEquals(1070.0 / 30.0, p.dailyRate(BASE), EPS);
        assertTrue(p.requiresInstallmentSchedule());
    }

    // --- End date calculation ---

    @Test
    void monthlyEndDate_isStartPlus30Days() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        assertEquals(LocalDate.of(2026, 7, 1),
                new MonthlyPackage().calculateEndDate(start));
    }

    @Test
    void annualEndDate_isStartPlus365Days() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate expected = start.plusDays(365);
        assertEquals(expected,
                new AnnualPrepaidPackage().calculateEndDate(start));
        assertEquals(expected,
                new AnnualInstallmentPackage().calculateEndDate(start));
    }

    // --- Registry ---

    @Test
    void registry_returnsCorrectStrategyPerPackage() {
        assertEquals("MONTHLY",
                PackageStrategyRegistry.forPackage("MONTHLY").packageType());
        assertEquals("ANNUAL_PREPAID",
                PackageStrategyRegistry.forPackage("ANNUAL_PREPAID").packageType());
        assertEquals("ANNUAL_INSTALLMENT",
                PackageStrategyRegistry.forPackage("ANNUAL_INSTALLMENT").packageType());
    }

    @Test
    void registry_unknownPackage_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> PackageStrategyRegistry.forPackage("LIFETIME"));
    }
}