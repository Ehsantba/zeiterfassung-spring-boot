package de.zeiterfassung.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gesetzliche Feiertage in Nordrhein-Westfalen. Bewegliche Feiertage werden aus dem Ostersonntag berechnet
 * (Algorithmus nach Meeus/Jones/Butcher), es sind also keine Jahreszahlen fest hinterlegt.
 */
public final class HolidayCalendar {

    private static final Map<Integer, Map<LocalDate, String>> CACHE = new ConcurrentHashMap<>();

    private HolidayCalendar() {
    }

    public static Map<LocalDate, String> holidays(int year) {
        return CACHE.computeIfAbsent(year, HolidayCalendar::compute);
    }

    public static Optional<String> holidayName(LocalDate date) {
        return Optional.ofNullable(holidays(date.getYear()).get(date));
    }

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Werktag im Sinne der Soll-Zeit: Montag bis Freitag und kein Feiertag. */
    public static boolean isWorkday(LocalDate date) {
        return !isWeekend(date) && holidayName(date).isEmpty();
    }

    public static int countWorkdays(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isWorkday(d)) {
                count++;
            }
        }
        return count;
    }

    public static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private static Map<LocalDate, String> compute(int year) {
        LocalDate easter = easterSunday(year);
        Map<LocalDate, String> map = new TreeMap<>();
        map.put(LocalDate.of(year, 1, 1), "Neujahr");
        map.put(easter.minusDays(2), "Karfreitag");
        map.put(easter.plusDays(1), "Ostermontag");
        map.put(LocalDate.of(year, 5, 1), "Tag der Arbeit");
        map.put(easter.plusDays(39), "Christi Himmelfahrt");
        map.put(easter.plusDays(50), "Pfingstmontag");
        map.put(easter.plusDays(60), "Fronleichnam");
        map.put(LocalDate.of(year, 10, 3), "Tag der Deutschen Einheit");
        map.put(LocalDate.of(year, 11, 1), "Allerheiligen");
        map.put(LocalDate.of(year, 12, 25), "1. Weihnachtstag");
        map.put(LocalDate.of(year, 12, 26), "2. Weihnachtstag");
        return Map.copyOf(map);
    }
}
