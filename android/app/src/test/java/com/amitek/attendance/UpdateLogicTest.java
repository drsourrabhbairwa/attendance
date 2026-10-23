package com.amitek.attendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class UpdateLogicTest {
    private static final String BASE = "https://github.com/drsourrabhbairwa/attendance/releases/download/app-v9.1/";

    private static JSONArray assets(String... nameUrlSize) throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 0; i < nameUrlSize.length; i += 3)
            a.put(new JSONObject().put("name", nameUrlSize[i]).put("browser_download_url", nameUrlSize[i + 1]).put("size", Long.parseLong(nameUrlSize[i + 2])));
        return a;
    }

    @Test public void tagGivesTheBuildNumber() {
        assertEquals(12, UpdateLogic.buildOfTag("app-v12.1"));
        assertEquals(7, UpdateLogic.buildOfTag("app-v7"));
        assertEquals(30, UpdateLogic.buildOfTag(" app-v30.2 "));
    }

    @Test public void aTagThatIsNotOursIsUnknown() {
        assertEquals(-1, UpdateLogic.buildOfTag(null));
        assertEquals(-1, UpdateLogic.buildOfTag(""));
        assertEquals(-1, UpdateLogic.buildOfTag("v12"));
        assertEquals(-1, UpdateLogic.buildOfTag("app-vabc"));
        assertEquals(-1, UpdateLogic.buildOfTag("app-v12.1-beta"));
        assertEquals(-1, UpdateLogic.buildOfTag("app-v99999999999.1"));
    }

    @Test public void onlyAHigherKnownBuildIsNewer() {
        assertTrue(UpdateLogic.newer(6, 7));
        assertFalse(UpdateLogic.newer(7, 7));
        assertFalse(UpdateLogic.newer(8, 7));
        assertFalse(UpdateLogic.newer(6, -1));
        assertFalse(UpdateLogic.newer(6, 0));
    }

    @Test public void theFloorForcesOnlyBuildsBelowIt() {
        assertTrue(UpdateLogic.forced(6, 7));
        assertFalse(UpdateLogic.forced(7, 7));
        assertFalse(UpdateLogic.forced(6, 0));
        assertFalse(UpdateLogic.forced(6, -3));
    }

    @Test public void apkUrlIsTheNamedAssetOnOurOwnReleases() throws Exception {
        assertEquals(BASE + "amitek-attendance.apk", UpdateLogic.apkUrl(assets(
                "notes.txt", BASE + "notes.txt", "10",
                "amitek-attendance.apk", BASE + "amitek-attendance.apk", "4200000")));
    }

    @Test public void apkUrlRefusesAnythingNotFromOurReleases() throws Exception {
        assertEquals("", UpdateLogic.apkUrl(null));
        assertEquals("", UpdateLogic.apkUrl(new JSONArray()));
        assertEquals("", UpdateLogic.apkUrl(assets("amitek-attendance.apk", "https://evil.example/amitek-attendance.apk", "1")));
        assertEquals("", UpdateLogic.apkUrl(assets("amitek-attendance.apk", "http://github.com/drsourrabhbairwa/attendance/releases/download/app-v9.1/amitek-attendance.apk", "1")));
        assertEquals("", UpdateLogic.apkUrl(assets("amitek-attendance.apk", "https://github.com/someone/else/releases/download/app-v9.1/amitek-attendance.apk", "1")));
        assertEquals("", UpdateLogic.apkUrl(assets("amitek-attendance.apk", "https://github.com/drsourrabhbairwa/attendance/releases/download/../../../../evil/x.apk", "1")));
        assertEquals("", UpdateLogic.apkUrl(assets("other.apk", BASE + "other.apk", "1")));
    }

    @Test public void apkSizeComesFromTheSameAsset() throws Exception {
        assertEquals(4200000L, UpdateLogic.apkSize(assets("notes.txt", BASE + "notes.txt", "10", "amitek-attendance.apk", BASE + "amitek-attendance.apk", "4200000")));
        assertEquals(0L, UpdateLogic.apkSize(null));
        assertEquals(0L, UpdateLogic.apkSize(new JSONArray()));
    }

    @Test public void minBuildReadsTheFloorAndDefaultsToNone() {
        assertEquals(8, UpdateLogic.minBuildFrom("{\"web\":\"2026-10-22.1\",\"minBuild\":8}"));
        assertEquals(9, UpdateLogic.minBuildFrom("{\"minBuild\":\"9\"}"));
        assertEquals(0, UpdateLogic.minBuildFrom("{}"));
        assertEquals(0, UpdateLogic.minBuildFrom("<html>not json</html>"));
        assertEquals(0, UpdateLogic.minBuildFrom(null));
        assertEquals(0, UpdateLogic.minBuildFrom("{\"minBuild\":-4}"));
    }

    @Test public void notesAreShortPlainText() {
        assertEquals("", UpdateLogic.cleanNotes(null));
        assertEquals("What changed\nPush works when the app is closed", UpdateLogic.cleanNotes("## What changed\r\n**Push** works when the app is closed"));
        String long600 = new String(new char[900]).replace('\0', 'x');
        String cut = UpdateLogic.cleanNotes(long600);
        assertTrue(cut.length() <= 601);
        assertTrue(cut.endsWith("…"));
    }

    private static String release(String tag, boolean draft, boolean pre, String assetsJson) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":" + draft + ",\"prerelease\":" + pre + ",\"body\":\"## New\\n**Push** when closed\",\"assets\":" + assetsJson + "}";
    }

    private static final String GOOD_ASSETS = "[{\"name\":\"amitek-attendance.apk\",\"size\":4200000,\"browser_download_url\":\"" + BASE + "amitek-attendance.apk\"}]";

    @Test public void aGoodReleaseGivesBuildLinkSizeAndNotes() {
        UpdateLogic.Release r = UpdateLogic.parseRelease(release("app-v9.1", false, false, GOOD_ASSETS));
        assertEquals(9, r.build);
        assertEquals(BASE + "amitek-attendance.apk", r.url);
        assertEquals(4200000L, r.size);
        assertEquals("New\nPush when closed", r.notes);
    }

    @Test public void aReleaseThatCannotBeInstalledIsIgnored() {
        assertEquals(null, UpdateLogic.parseRelease(release("app-v9.1", true, false, GOOD_ASSETS)));    // draft
        assertEquals(null, UpdateLogic.parseRelease(release("app-v9.1", false, true, GOOD_ASSETS)));    // pre-release
        assertEquals(null, UpdateLogic.parseRelease(release("latest", false, false, GOOD_ASSETS)));     // not one of our tags
        assertEquals(null, UpdateLogic.parseRelease(release("app-v9.1", false, false, "[]")));          // no APK attached
        assertEquals(null, UpdateLogic.parseRelease(release("app-v9.1", false, false,
                "[{\"name\":\"amitek-attendance.apk\",\"size\":1,\"browser_download_url\":\"https://evil.example/a.apk\"}]")));
        assertEquals(null, UpdateLogic.parseRelease("<html>rate limited</html>"));
        assertEquals(null, UpdateLogic.parseRelease(null));
    }

    @Test public void percentIsClampedAndSafe() {
        assertEquals(50, UpdateLogic.percent(50, 100));
        assertEquals(0, UpdateLogic.percent(0, 100));
        assertEquals(0, UpdateLogic.percent(10, 0));
        assertEquals(100, UpdateLogic.percent(200, 100));
        assertEquals(33, UpdateLogic.percent(1, 3));
    }
}
