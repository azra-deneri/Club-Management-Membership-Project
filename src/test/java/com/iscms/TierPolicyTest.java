package com.iscms;

import com.iscms.service.policy.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Unit tests verifying each TierPolicy implementation independently and
// covering the registry's dispatch + error path. No mocks or DB needed —
// policies are stateless, pure data.
public class TierPolicyTest {

    // --- Classic ---

    @Test
    void classic_oneFreeze_noPt_allRenewalTiersAvailable() {
        TierPolicy p = new ClassicTierPolicy();
        assertEquals("CLASSIC", p.tierName());
        assertEquals(1, p.maxFreezePerMonth());
        assertEquals(0, p.maxPtSessionsPerMonth());
        assertFalse(p.allowsPtSessions());
        assertEquals(750.0, p.baseMonthlyPrice());          // YENİ
        assertEquals(java.util.List.of("CLASSIC", "GOLD", "VIP"),
                p.availableRenewalTiers());
    }

    @Test
    void gold_twoFreezes_twoPtSessions_canUpgradeToVip() {
        TierPolicy p = new GoldTierPolicy();
        assertEquals("GOLD", p.tierName());
        assertEquals(2, p.maxFreezePerMonth());
        assertEquals(2, p.maxPtSessionsPerMonth());
        assertTrue(p.allowsPtSessions());
        assertEquals(1250.0, p.baseMonthlyPrice());         // YENİ
        assertEquals(java.util.List.of("GOLD", "VIP"),
                p.availableRenewalTiers());
    }

    @Test
    void vip_threeFreezes_fourPtSessions_noUpgradePath() {
        TierPolicy p = new VipTierPolicy();
        assertEquals("VIP", p.tierName());
        assertEquals(3, p.maxFreezePerMonth());
        assertEquals(4, p.maxPtSessionsPerMonth());
        assertTrue(p.allowsPtSessions());
        assertEquals(2000.0, p.baseMonthlyPrice());         // YENİ
        assertEquals(java.util.List.of("VIP"),
                p.availableRenewalTiers());
    }

    // --- Registry dispatch ---

    @Test
    void registry_returnsCorrectPolicyPerTier() {
        assertEquals("CLASSIC", TierPolicyRegistry.forTier("CLASSIC").tierName());
        assertEquals("GOLD",    TierPolicyRegistry.forTier("GOLD").tierName());
        assertEquals("VIP",     TierPolicyRegistry.forTier("VIP").tierName());
    }

    @Test
    void registry_unknownTier_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> TierPolicyRegistry.forTier("PLATINUM"));
    }


}