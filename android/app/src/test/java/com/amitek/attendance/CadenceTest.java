package com.amitek.attendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class CadenceTest {
    private static final int FOUR_PM = 16 * 60;

    @Test public void withoutEveningCheckTheGapIsTheUsualRandomOne() {
        for (int seed = 0; seed < 200; seed++) {
            int g = Cadence.nextGapMinutes(10 * 60, 45, 75, 0, 0, new Random(seed));
            assertTrue("gap " + g, g >= 45 && g <= 75);
            int late = Cadence.nextGapMinutes(18 * 60, 45, 75, 0, 15, new Random(seed));
            assertTrue("no evening start set: still the usual gap, got " + late, late >= 45 && late <= 75);
        }
    }

    @Test public void duringTheEveningItIsTheShortGap() {
        for (int seed = 0; seed < 200; seed++) {
            assertEquals(15, Cadence.nextGapMinutes(FOUR_PM, 45, 75, FOUR_PM, 15, new Random(seed)));
            assertEquals(15, Cadence.nextGapMinutes(18 * 60 + 20, 45, 75, FOUR_PM, 15, new Random(seed)));
        }
    }

    @Test public void aCheckNeverJumpsOverTheStartOfTheEvening() {
        for (int seed = 0; seed < 200; seed++) {
            int now = 15 * 60;   // 3 PM, evening checks start at 4 PM
            int g = Cadence.nextGapMinutes(now, 45, 75, FOUR_PM, 15, new Random(seed));
            assertTrue("gap " + g, g >= 1 && now + g <= FOUR_PM);
        }
    }

    @Test public void farBeforeTheEveningTheGapIsStillTheUsualOne() {
        for (int seed = 0; seed < 200; seed++) {
            int g = Cadence.nextGapMinutes(9 * 60, 45, 75, FOUR_PM, 15, new Random(seed));
            assertTrue("gap " + g, g >= 45 && g <= 75);
        }
    }

    @Test public void aReversedRangeStillGivesOneSensibleGap() {
        assertEquals(60, Cadence.nextGapMinutes(10 * 60, 60, 30, 0, 0, new Random(1)));
    }

    @Test public void theGapIsAtLeastOneMinute() {
        for (int now = 0; now < 1440; now += 7)
            assertTrue(Cadence.nextGapMinutes(now, 45, 75, FOUR_PM, 15, new Random(now)) >= 1);
    }
}
