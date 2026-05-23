package com.iscms;

import com.iscms.service.policy.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

// Unit tests for BMI advice strategies + registry dispatch.
public class BmiAdviceStrategyTest {

    @Test
    void underweight_threeStrengthAndNutritionTips() {
        BmiAdviceStrategy s = new UnderweightAdvice();
        assertEquals("UNDERWEIGHT", s.category());
        assertEquals(3, s.suggestions().length);
        assertTrue(s.suggestions()[0].contains("caloric"));
    }

    @Test
    void normal_threeMaintenanceTips() {
        BmiAdviceStrategy s = new NormalAdvice();
        assertEquals("NORMAL", s.category());
        assertEquals(3, s.suggestions().length);
        assertTrue(s.suggestions()[0].contains("Maintain"));
    }

    @Test
    void overweight_threeActivityAndDietTips() {
        BmiAdviceStrategy s = new OverweightAdvice();
        assertEquals("OVERWEIGHT", s.category());
        assertEquals(3, s.suggestions().length);
        assertTrue(s.suggestions()[0].contains("exercise"));
    }

    @Test
    void obese_threeMedicalAndLifestyleTips() {
        BmiAdviceStrategy s = new ObeseAdvice();
        assertEquals("OBESE", s.category());
        assertEquals(3, s.suggestions().length);
        assertTrue(s.suggestions()[0].contains("healthcare"));
    }

    @Test
    void registry_returnsCorrectStrategyPerCategory() {
        assertEquals("UNDERWEIGHT",
                BmiAdviceRegistry.forCategory("UNDERWEIGHT").category());
        assertEquals("NORMAL",
                BmiAdviceRegistry.forCategory("NORMAL").category());
        assertEquals("OVERWEIGHT",
                BmiAdviceRegistry.forCategory("OVERWEIGHT").category());
        assertEquals("OBESE",
                BmiAdviceRegistry.forCategory("OBESE").category());
    }

    @Test
    void registry_unknownOrNullCategory_returnsFallback() {
        // Null and unknown categories yield a single generic message rather
        // than crashing — keeps the BMI panel resilient
        assertEquals(1, BmiAdviceRegistry.forCategory(null).suggestions().length);
        assertEquals(1, BmiAdviceRegistry.forCategory("BANANA").suggestions().length);
        assertEquals("UNKNOWN", BmiAdviceRegistry.forCategory(null).category());
    }
}