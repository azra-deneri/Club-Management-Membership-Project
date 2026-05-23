package com.iscms.service.policy;

import java.util.Map;

// Central lookup that maps a tier name (DB string) to its policy instance.
// Replaces every tier-based switch in the codebase with a single dispatch call:
//     TierPolicyRegistry.forTier(membership.getTier()).maxFreezePerMonth()
//
// Instances are stateless singletons — safe to reuse across the application.
public final class TierPolicyRegistry {

    private static final Map<String, TierPolicy> POLICIES = Map.of(
            "CLASSIC", new ClassicTierPolicy(),
            "GOLD",    new GoldTierPolicy(),
            "VIP",     new VipTierPolicy()
    );

    private TierPolicyRegistry() { }   // utility class — no instances

    // Returns the policy for the given tier string.
    // Throws if the tier is unknown — fail fast rather than returning a default.
    public static TierPolicy forTier(String tier) {
        TierPolicy policy = POLICIES.get(tier);
        if (policy == null)
            throw new IllegalArgumentException("Unknown tier: " + tier);
        return policy;
    }
}