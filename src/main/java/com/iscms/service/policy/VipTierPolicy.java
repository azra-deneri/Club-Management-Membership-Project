package com.iscms.service.policy;

import java.util.List;

// Tier policy for VIP members — the top-level tier.
// 3 freezes per month, up to 4 PT sessions per month.
// VIP members may only stay at VIP — there's nowhere to upgrade.
public class VipTierPolicy implements TierPolicy {

    @Override public String tierName()          { return "VIP"; }
    @Override public int maxFreezePerMonth()    { return 3; }
    @Override public int maxPtSessionsPerMonth(){ return 4; }

    @Override public List<String> availableRenewalTiers() {
        return List.of("VIP");
    }

    @Override public double baseMonthlyPrice() { return 2000.0; }
}