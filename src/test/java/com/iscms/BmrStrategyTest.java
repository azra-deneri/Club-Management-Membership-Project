package com.iscms;

import com.iscms.service.policy.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Unit tests verifying each BmrStrategy implementation independently and
// the registry's dispatch + fallback behavior. Expected values computed
// by hand from the Harris-Benedict formula.
public class BmrStrategyTest {

    private static final double EPS = 0.01;

    // 30-year-old male, 80 kg, 180 cm
    //   = 88.362 + 13.397*80 + 4.799*180 - 5.677*30
    //   = 88.362 + 1071.76 + 863.82 - 170.31 = 1853.632
    @Test
    void male_returnsHarrisBenedictValue() {
        BmrStrategy s = new MaleBmrStrategy();
        assertEquals("MALE", s.gender());
        assertEquals(1853.632, s.calculate(80, 180, 30), EPS);
    }

    // 25-year-old female, 60 kg, 165 cm
    //   = 447.593 + 9.247*60 + 3.098*165 - 4.330*25
    //   = 447.593 + 554.82 + 511.17 - 108.25 = 1405.333
    @Test
    void female_returnsHarrisBenedictValue() {
        BmrStrategy s = new FemaleBmrStrategy();
        assertEquals("FEMALE", s.gender());
        assertEquals(1405.333, s.calculate(60, 165, 25), EPS);
    }

    @Test
    void registry_returnsCorrectStrategyPerGender() {
        assertEquals("MALE",   BmrStrategyRegistry.forGender("MALE").gender());
        assertEquals("FEMALE", BmrStrategyRegistry.forGender("FEMALE").gender());
    }

    @Test
    void registry_unknownGender_fallsBackToFemale() {
        // Conservative default — null, empty, or unexpected values use
        // the lower-calorie female formula rather than throwing
        assertEquals("FEMALE", BmrStrategyRegistry.forGender(null).gender());
        assertEquals("FEMALE", BmrStrategyRegistry.forGender("OTHER").gender());
        assertEquals("FEMALE", BmrStrategyRegistry.forGender("").gender());
    }
}