package com.amitek.attendance;

/** The small decisions behind push messages and the answer buttons, kept free of Android classes so they can be unit-tested. */
final class PushLogic {
    private PushLogic() {}

    /** Which channel a message from the office uses; anything unknown is a reminder (and a server message can never pick the silent channel). */
    static String channelOf(String name) {
        if (Push.CH_ALARM.equals(name) || Push.CH_REPORTS.equals(name) || Push.CH_UPDATES.equals(name)) return name;
        return Push.CH_REMINDERS;
    }

    /** The screen a tap opens; only the page's known screens are passed on. */
    static String routeOf(String r) {
        if ("today".equals(r) || "out".equals(r) || "tasks".equals(r) || "store".equals(r)) return r;
        return "notices";
    }

    /** A message (or a button press) is for the person signed in on this phone. A message that names nobody counts; with nobody signed in nothing does. */
    static boolean forPerson(String to, String signedIn) {
        if (signedIn == null || signedIn.isEmpty()) return false;
        return to == null || to.isEmpty() || to.equalsIgnoreCase(signedIn);
    }

    /** With the app open the page shows the message itself; urgent ones still ring, and the weekly summary still gets its notification. */
    static boolean pageShows(String kind, String channel, boolean appVisible) {
        return appVisible && !Push.CH_ALARM.equals(channel) && !"weekly".equals(kind);
    }

    /** "06:42:00 PM" -> "06:42 PM". */
    static String shortClock(String t) {
        if (t == null) return "";
        String s = t.trim();
        return s.replaceFirst("^(\\d{1,2}:\\d{2}):\\d{2}(\\s*[AaPp][Mm])$", "$1$2");
    }

    /** Title and text of the notice that replaces the question once it was answered. */
    static String[] answer(boolean yes, String officeTime, String askedRef) {
        if (!yes) return new String[]{"Noted: you are still working", "If you are still away in about two hours, we will ask again."};
        String at = shortClock(officeTime);
        if (at.isEmpty()) at = shortClock(askedRef);
        return new String[]{"✓ Punched out" + (at.isEmpty() ? "" : " at " + at), "Thank you. Your day is closed."};
    }
}
