package com.iscms.service.policy;

import java.util.Map;

// Central lookup that maps a package type string to its strategy instance.
// Replaces every package-based switch in the codebase with a dispatch call:
//     PackageStrategyRegistry.forPackage(ms.getPackageType()).durationInDays()
public final class PackageStrategyRegistry {

    private static final Map<String, PackageStrategy> STRATEGIES = Map.of(
            "MONTHLY",            new MonthlyPackage(),
            "ANNUAL_PREPAID",     new AnnualPrepaidPackage(),
            "ANNUAL_INSTALLMENT", new AnnualInstallmentPackage()
    );

    private PackageStrategyRegistry() { }

    public static PackageStrategy forPackage(String packageType) {
        PackageStrategy strategy = STRATEGIES.get(packageType);
        if (strategy == null)
            throw new IllegalArgumentException("Unknown package type: " + packageType);
        return strategy;
    }
}