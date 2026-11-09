package de.zeiterfassung;

import static org.assertj.core.api.Assertions.assertThat;

import de.zeiterfassung.service.WorkTime;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class WorkTimeTest {

    @Test
    void normalWorkday_isNetOfBreak() {
        LocalDate day = LocalDate.of(2025, 6, 18);
        assertThat(WorkTime.grossMinutes(day, LocalTime.of(8, 0), LocalTime.of(16, 30))).isEqualTo(510);
        assertThat(WorkTime.netMinutes(day, LocalTime.of(8, 0), LocalTime.of(16, 30), 30)).isEqualTo(480);
    }

    @Test
    void springForward_nightShiftHasOneHourLess() {
        // 30.03.2025: Um 02:00 springt die Uhr auf 03:00. Zwischen 01:00 und 04:00 liegen real nur 2 Stunden.
        LocalDate day = LocalDate.of(2025, 3, 30);
        assertThat(WorkTime.grossMinutes(day, LocalTime.of(1, 0), LocalTime.of(4, 0))).isEqualTo(120);
    }

    @Test
    void fallBack_hasOneHourMore() {
        // 26.10.2025: Zwischen 02:00 und 03:00 gibt es die Stunde zweimal. 01:00 bis 04:00 sind real 4 Stunden.
        LocalDate day = LocalDate.of(2025, 10, 26);
        assertThat(WorkTime.grossMinutes(day, LocalTime.of(1, 0), LocalTime.of(4, 0))).isEqualTo(240);
    }

    @Test
    void breakLongerThanWork_neverGoesNegative() {
        LocalDate day = LocalDate.of(2025, 6, 18);
        assertThat(WorkTime.netMinutes(day, LocalTime.of(8, 0), LocalTime.of(9, 0), 120)).isZero();
    }
}
