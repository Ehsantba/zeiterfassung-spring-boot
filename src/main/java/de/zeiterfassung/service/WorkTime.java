package de.zeiterfassung.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Berechnung von Arbeitszeiten in der Zeitzone Europe/Berlin.
 *
 * Die Dauer wird über ZonedDateTime berechnet, nicht über reine Uhrzeiten. Dadurch stimmt die
 * tatsächlich gearbeitete Zeit auch an Tagen der Sommer-/Winterzeitumstellung
 * (Frühjahr: 02:00 springt auf 03:00, Herbst: 02:00 bis 03:00 gibt es zweimal).
 */
public final class WorkTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    public static final long DAILY_TARGET_MINUTES = 8 * 60;
    public static final long LONG_DAY_WARNING_MINUTES = 10 * 60;

    private WorkTime() {
    }

    public static long grossMinutes(LocalDate date, LocalTime start, LocalTime end) {
        return Duration.between(date.atTime(start).atZone(ZONE), date.atTime(end).atZone(ZONE)).toMinutes();
    }

    public static long netMinutes(LocalDate date, LocalTime start, LocalTime end, int breakMinutes) {
        return Math.max(0, grossMinutes(date, start, end) - breakMinutes);
    }
}
