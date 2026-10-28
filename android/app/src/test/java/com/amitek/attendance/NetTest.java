package com.amitek.attendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetTest {
    @Test public void withoutABudgetTheUsualTimeoutApplies() {
        assertEquals(15000, Net.timeoutFor(0, 1_000, 15000));
    }

    @Test public void aBudgetShortensEachStepToWhatIsLeft() {
        assertEquals(15000, Net.timeoutFor(100_000, 1_000, 15000));      // plenty left: the usual wait
        assertEquals(3000, Net.timeoutFor(10_000, 7_000, 15000));        // 3 s left
        assertEquals(1000, Net.timeoutFor(10_000, 9_900, 15000));        // nearly out: still a real try
        assertEquals(1000, Net.timeoutFor(10_000, 12_000, 15000));       // already out
    }

    @Test public void aReplyThatSaysStoppedMeansTheJobIsOver() throws Exception {
        assertTrue(Net.jobIsOver(new org.json.JSONObject("{\"ok\":true,\"data\":{\"stopped\":true}}")));
    }

    @Test public void anyOtherReplyLeavesTheJobRunning() throws Exception {
        assertFalse(Net.jobIsOver(new org.json.JSONObject("{\"ok\":true,\"data\":null}")));
        assertFalse(Net.jobIsOver(new org.json.JSONObject("{\"ok\":true}")));
        assertFalse(Net.jobIsOver(new org.json.JSONObject("{\"ok\":true,\"data\":{\"stopped\":false}}")));
        assertFalse(Net.jobIsOver(new org.json.JSONObject("{\"ok\":false,\"error\":\"x\"}")));
        assertFalse(Net.jobIsOver(null));
    }
}
