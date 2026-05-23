package com.iscms.service.policy;

import java.util.List;

// Strategy contract for tier-specific business rules.
// Each concrete policy encapsulates the rules for a single membership tier
// (CLASSIC, GOLD, VIP), replacing tier-based switch/if-else blocks scattered
// across MemberService, PTService, and the UI.
public interface TierPolicy {

    // Identifier matching the tier string used in DB and DTOs.
    String tierName();

    // Maximum number of freezes allowed for an active membership.
    int maxFreezePerMonth();

    // Maximum PT sessions a member of this tier can book per month.
    // A value of 0 means PT is not available for this tier.
    int maxPtSessionsPerMonth();

    // Tiers offered to this member at renewal time.
    // Includes the current tier (no-change) plus any upgrade targets.
    List<String> availableRenewalTiers();

    // Convenience flag — true if this tier may book PT sessions at all.
    default boolean allowsPtSessions() {
        return maxPtSessionsPerMonth() > 0;
    }

    // Base monthly price for this tier, in TL. Package strategies multiply
// this by their discount / markup / duration factors to produce the
// final charge.
    double baseMonthlyPrice();
}