package com.amitek.attendance;

import java.util.Random;

/**
 * When the next location check is due. By day it is the usual random 45-75 minutes. From the evening time (the office's
 * "forgotten punch-out" check start, 4 PM by default) it is every few minutes, so that a person who has walked away is
 * seen within about a quarter of an hour. Free of Android classes so it can be unit-tested.
 */
final class Cadence {
    private Cadence() {}

    /**
     * @param nowMin minute of the day now (0..1439)
     * @param min    shortest usual gap in minutes
     * @param max    longest usual gap in minutes
     * @param eveMin minute of the day the evening checks start (0 = never)
     * @param eveGap gap in minutes during the evening (0 = never)
     * @return the gap to the next check, in minutes (at least 1)
     */
    static int nextGapMinutes(int nowMin, int min, int max, int eveMin, int eveGap, Random r) {
        int hi = Math.max(min, max);
        int usual = Math.max(1, min + (hi > min ? r.nextInt(hi - min + 1) : 0));
        if (eveMin <= 0 || eveGap <= 0) return usual;
        if (nowMin >= eveMin) return Math.max(1, Math.min(usual, eveGap));
        if (nowMin + usual > eveMin) return Math.max(1, eveMin - nowMin);   // would jump over the start: land on it
        return usual;
    }
}
