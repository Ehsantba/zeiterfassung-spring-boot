package de.zeiterfassung;

import static org.assertj.core.api.Assertions.assertThat;

import de.zeiterfassung.service.HolidayCalendar;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HolidayCalendarTest {

    @Test
    void easterSunday_forKnownYears() {
        assertThat(HolidayCalendar.easterSunday(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(HolidayCalendar.easterSunday(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(HolidayCalendar.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(HolidayCalendar.easterSunday(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void movableHolidays2025() {
        var h = HolidayCalendar.holidays(2025);
        assertThat(h).containsEntry(LocalDate.of(2025, 4, 18), "Karfreitag")
                .containsEntry(LocalDate.of(2025, 4, 21), "Ostermontag")
                .containsEntry(LocalDate.of(2025, 5, 29), "Christi Himmelfahrt")
                .containsEntry(LocalDate.of(2025, 6, 9), "Pfingstmontag")
                .containsEntry(LocalDate.of(2025, 6, 19), "Fronleichnam");
    }

    @Test
    void movableHolidays2026() {
        var h = HolidayCalendar.holidays(2026);
        assertThat(h).containsEntry(LocalDate.of(2026, 4, 3), "Karfreitag")
                .containsEntry(LocalDate.of(2026, 4, 6), "Ostermontag")
                .containsEntry(LocalDate.of(2026, 5, 14), "Christi Himmelfahrt")
                .containsEntry(LocalDate.of(2026, 5, 25), "Pfingstmontag")
                .containsEntry(LocalDate.of(2026, 6, 4), "Fronleichnam");
    }

    @Test
    void northRhineWestphaliaHasElevenHolidays_andNotTheOnesOfOtherStates() {
        var h = HolidayCalendar.holidays(2025);
        assertThat(h).hasSize(11);
        assertThat(h).containsKey(LocalDate.of(2025, 11, 1));       // Allerheiligen gilt in NRW
        assertThat(h).doesNotContainKey(LocalDate.of(2025, 10, 31)); // Reformationstag nicht
        assertThat(h).doesNotContainKey(LocalDate.of(2025, 1, 6));   // Heilige Drei Könige nicht
    }

    @Test
    void workdaysSkipWeekendsAndHolidays() {
        // 22.12. bis 31.12.2025: 22., 23., 24., 29., 30., 31. sind Werktage, 25. und 26. Feiertage
        assertThat(HolidayCalendar.countWorkdays(LocalDate.of(2025, 12, 22), LocalDate.of(2025, 12, 31))).isEqualTo(6);
        assertThat(HolidayCalendar.isWorkday(LocalDate.of(2025, 6, 9))).isFalse();  // Pfingstmontag
        assertThat(HolidayCalendar.isWorkday(LocalDate.of(2025, 6, 14))).isFalse(); // Samstag
        assertThat(HolidayCalendar.isWorkday(LocalDate.of(2025, 6, 18))).isTrue();
    }
}
