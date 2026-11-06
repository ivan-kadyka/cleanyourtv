package tv.cleanyour.trending;

import java.util.HashMap;
import java.util.Map;

/** Kinopoisk returns genres and countries in Russian only; these tables give the English names. */
final class Translate {
    private Translate() { }

    private static final Map<String, String> GENRES = new HashMap<>();
    private static final Map<String, String> COUNTRIES = new HashMap<>();

    static {
        String[] g = {
            "триллер", "Thriller", "драма", "Drama", "криминал", "Crime", "мелодрама", "Romance",
            "детектив", "Mystery", "фантастика", "Sci-Fi", "приключения", "Adventure", "биография", "Biography",
            "фильм-нуар", "Film-Noir", "вестерн", "Western", "боевик", "Action", "фэнтези", "Fantasy",
            "комедия", "Comedy", "военный", "War", "история", "History", "музыка", "Music", "ужасы", "Horror",
            "мультфильм", "Animation", "семейный", "Family", "мюзикл", "Musical", "спорт", "Sport",
            "документальный", "Documentary", "короткометражка", "Short", "аниме", "Anime", "новости", "News",
            "концерт", "Concert", "для взрослых", "Adult", "церемония", "Ceremony", "реальное ТВ", "Reality-TV",
            "игра", "Game-Show", "ток-шоу", "Talk-Show", "детский", "Kids",
        };
        for (int i = 0; i < g.length; i += 2) GENRES.put(g[i], g[i + 1]);
        String[] c = {
            "США", "USA", "Великобритания", "UK", "Россия", "Russia", "Франция", "France", "Германия", "Germany",
            "Канада", "Canada", "Италия", "Italy", "Испания", "Spain", "Япония", "Japan", "Корея Южная", "South Korea",
            "Китай", "China", "Индия", "India", "Австралия", "Australia", "Польша", "Poland", "Украина", "Ukraine",
            "Беларусь", "Belarus", "Казахстан", "Kazakhstan", "Швеция", "Sweden", "Норвегия", "Norway",
            "Дания", "Denmark", "Финляндия", "Finland", "Нидерланды", "Netherlands", "Бельгия", "Belgium",
            "Ирландия", "Ireland", "Новая Зеландия", "New Zealand", "Мексика", "Mexico", "Бразилия", "Brazil",
            "Аргентина", "Argentina", "Турция", "Turkey", "Чехия", "Czechia", "Венгрия", "Hungary",
            "Австрия", "Austria", "Швейцария", "Switzerland", "Гонконг", "Hong Kong", "Тайвань", "Taiwan",
            "ЮАР", "South Africa", "Исландия", "Iceland", "Португалия", "Portugal", "Греция", "Greece",
            "Таиланд", "Thailand", "Индонезия", "Indonesia", "Филиппины", "Philippines", "Колумбия", "Colombia",
            "Чили", "Chile", "Израиль", "Israel", "ОАЭ", "UAE", "Египет", "Egypt", "Румыния", "Romania",
            "Болгария", "Bulgaria", "Сербия", "Serbia", "Хорватия", "Croatia", "Литва", "Lithuania",
            "Латвия", "Latvia", "Эстония", "Estonia", "Грузия", "Georgia", "Армения", "Armenia",
        };
        for (int i = 0; i < c.length; i += 2) COUNTRIES.put(c[i], c[i + 1]);
    }

    /** The English genre, or the Russian one with a capital letter when the table doesn't know it. */
    static String genre(String ru) {
        String en = GENRES.get(ru);
        return en != null ? en : capitalise(ru);
    }

    static String country(String ru) {
        String en = COUNTRIES.get(ru);
        return en != null ? en : ru;
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
