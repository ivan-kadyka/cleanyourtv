package tv.cleanyour.wallpapers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class BingTest {
    private static final String ANSWER = "{\"images\":["
            + "{\"startdate\":\"20261030\",\"urlbase\":\"/th?id=OHR.AutumnCranes_EN-US9795571462\","
            + "\"copyright\":\"Sandhill cranes, Bosque del Apache, New Mexico, USA (© Terry/Getty Images)\"},"
            + "{\"startdate\":\"20261024\",\"urlbase\":\"/th?id=OHR.PortugueseLibrary_EN-US7519268149\","
            + "\"copyright\":\"Royal Portuguese Cabinet of Reading Library, Rio de Janeiro (© A/Shutterstock)\"},"
            + "{\"startdate\":\"bad\",\"urlbase\":\"/th?id=OHR.X_EN-US1\",\"copyright\":\"x\"}]}";

    @Test
    public void parsesNatureAndSkipsInteriors() throws Exception {
        List<Bing.Picture> pics = Bing.parse(ANSWER);
        assertEquals(1, pics.size());
        assertEquals("20261030-AutumnCranes", pics.get(0).id);
        assertEquals("https://www.bing.com/th?id=OHR.AutumnCranes_EN-US9795571462_1920x1080.jpg", pics.get(0).url);
        assertTrue(pics.get(0).caption.startsWith("Sandhill cranes"));
    }

    @Test
    public void idsAreFileSafe() {
        assertTrue(Bing.validId("20261030-AutumnCranes"));
        assertFalse(Bing.validId("../x"));
        assertFalse(Bing.validId(""));
    }

    @Test
    public void evictsLeastRecentlyShownButNotTheNewBatch() {
        java.util.List<Fetcher.Entry> e = new java.util.ArrayList<>();
        e.add(new Fetcher.Entry("a", "", 100, 900));   // shown recently: stays
        e.add(new Fetcher.Entry("b", "", 200, 0));     // never shown, older: goes first
        e.add(new Fetcher.Entry("c", "", 300, 0));
        e.add(new Fetcher.Entry("d", "", 50, 0));      // just fetched: protected
        java.util.List<Fetcher.Entry> left = Fetcher.evict(e, java.util.Collections.singleton("d"), 2);
        assertEquals(2, left.size());
        assertEquals("a", left.get(0).id);
        assertEquals("d", left.get(1).id);
    }
}
