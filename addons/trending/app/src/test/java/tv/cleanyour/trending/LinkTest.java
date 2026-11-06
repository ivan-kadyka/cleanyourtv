package tv.cleanyour.trending;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LinkTest {
    @Test
    public void fillsKinopoiskId() {
        assertEquals("hd://videoboxalfa/film?id=301", Link.fill("hd://videoboxalfa/film?id={kp}", 301, "tt0133093", "The Matrix"));
    }

    @Test
    public void encodesTitle() {
        assertEquals("x://s?q=The+Matrix%3A+Reloaded", Link.fill("x://s?q={title}", 0, "", "The Matrix: Reloaded"));
    }

    @Test
    public void missingIdGivesNoLink() {
        assertEquals("", Link.fill("x://{kp}", 0, "", "x"));
        assertEquals("", Link.fill(Link.DEFAULT_TEMPLATE, 5, "", "x"));
        assertEquals("", Link.fill("x://{imdb}", 5, "", "x"));
    }

    @Test
    public void translatesGenresAndCountries() {
        assertEquals("Sci-Fi", Translate.genre("фантастика"));
        assertEquals("South Korea", Translate.country("Корея Южная"));
        assertEquals("Неизвестный", Translate.genre("неизвестный"));
    }
}
