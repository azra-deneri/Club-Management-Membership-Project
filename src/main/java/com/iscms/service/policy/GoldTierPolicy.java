package com.iscms.service.policy;

import java.util.List;

// Tier policy for GOLD members — the mid-level tier.
// 2 freezes per month, up to 2 PT sessions per month.
// GOLD members may renew at GOLD (stay) or upgrade to VIP — never downgrade.
public class GoldTierPolicy implements TierPolicy {

    @Override public String tierName()          { return "GOLD"; }
    @Override public int maxFreezePerMonth()    { return 2; }
    @Override public int maxPtSessionsPerMonth(){ return 2; }

    @Override public List<String> availableRenewalTiers() {
        return List.of("GOLD", "VIP");
    }

    @Override public double baseMonthlyPrice() { return 1250.0; }
}