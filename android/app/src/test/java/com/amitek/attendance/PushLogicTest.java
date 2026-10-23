package com.amitek.attendance;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PushLogicTest {
    @Test public void unknownChannelsAreReminders() {
        assertEquals("alarm", PushLogic.channelOf("alarm"));
        assertEquals("reports", PushLogic.channelOf("reports"));
        assertEquals("updates", PushLogic.channelOf("updates"));
        assertEquals("reminders", PushLogic.channelOf("reminders"));
        assertEquals("reminders", PushLogic.channelOf("quiet"));   // a server message can never pick the silent channel
        assertEquals("reminders", PushLogic.channelOf(null));
        assertEquals("reminders", PushLogic.channelOf("whatever"));
    }

    @Test public void onlyTheKnownScreensCanBeOpened() {
        assertEquals("today", PushLogic.routeOf("today"));
        assertEquals("out", PushLogic.routeOf("out"));
        assertEquals("tasks", PushLogic.routeOf("tasks"));
        assertEquals("store", PushLogic.routeOf("store"));
        assertEquals("notices", PushLogic.routeOf("notices"));
        assertEquals("notices", PushLogic.routeOf("javascript:alert(1)"));
        assertEquals("notices", PushLogic.routeOf(null));
    }

    @Test public void aMessageIsForTheSignedInPersonOnly() {
        assertTrue(PushLogic.forPerson("E1", "E1"));
        assertTrue(PushLogic.forPerson("e1", "E1"));
        assertTrue(PushLogic.forPerson("", "E1"));          // an older message that names nobody
        assertTrue(PushLogic.forPerson(null, "E1"));
        assertFalse(PushLogic.forPerson("E2", "E1"));
        assertFalse(PushLogic.forPerson("E1", ""));         // nobody is signed in on this phone
        assertFalse(PushLogic.forPerson("E1", null));
    }

    @Test public void whileTheAppIsOpenThePageShowsItUnlessItIsUrgentOrAWeeklySummary() {
        assertTrue(PushLogic.pageShows("task", "reminders", true));
        assertTrue(PushLogic.pageShows("report", "reports", true));
        assertFalse(PushLogic.pageShows("locationCheck", "alarm", true));    // the alarm always rings
        assertFalse(PushLogic.pageShows("weekly", "reports", true));         // the page keeps it in the bell only; the summary should still be seen
        assertFalse(PushLogic.pageShows("task", "reminders", false));        // app closed: always the notification list
    }

    @Test public void theClockLosesItsSeconds() {
        assertEquals("06:42 PM", PushLogic.shortClock("06:42:00 PM"));
        assertEquals("6:05 AM", PushLogic.shortClock("6:05:33 AM"));
        assertEquals("06:42 PM", PushLogic.shortClock("06:42 PM"));
        assertEquals("", PushLogic.shortClock(null));
        assertEquals("soon", PushLogic.shortClock(" soon "));
    }

    @Test public void theAnswerNoticeUsesTheTimeTheOfficeRecorded() {
        assertArrayEquals(new String[]{"✓ Punched out at 06:42 PM", "Thank you. Your day is closed."}, PushLogic.answer(true, "06:42:00 PM", "06:40 PM"));
        assertArrayEquals(new String[]{"✓ Punched out at 06:40 PM", "Thank you. Your day is closed."}, PushLogic.answer(true, "", "06:40 PM"));
        assertArrayEquals(new String[]{"✓ Punched out", "Thank you. Your day is closed."}, PushLogic.answer(true, null, ""));
        String[] no = PushLogic.answer(false, null, "06:40 PM");
        assertEquals("Noted: you are still working", no[0]);
        assertFalse("the office does ask again later, so the notice must not promise otherwise", no[1].toLowerCase().contains("will not ask"));
        assertTrue(no[1].toLowerCase().contains("again"));
    }
}
