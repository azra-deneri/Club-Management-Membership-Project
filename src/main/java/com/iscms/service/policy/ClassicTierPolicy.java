package com.iscms.service.policy;

import java.util.List;

// Tier policy for CLASSIC members — the entry-level tier.
// CLASSIC has the most restrictive limits: 1 freeze, no PT access.
// CLASSIC members may renew at any tier (stay, or upgrade to GOLD/VIP).
public class ClassicTierPolicy implements TierPolicy {

    @Override public String tierName()          { return "CLASSIC"; }
    @Override public int maxFreezePerMonth()    { return 1; }
    @Override public int maxPtSessionsPerMonth(){ return 0; }   // PT blocked

    @Override public List<String> availableRenewalTiers() {
        return List.of("CLASSIC", "GOLD", "VIP");
    }

    @Override public double baseMonthlyPrice() { return 750.0; }
}