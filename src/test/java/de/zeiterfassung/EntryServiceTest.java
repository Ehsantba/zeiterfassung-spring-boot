package de.zeiterfassung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.Role;
import de.zeiterfassung.domain.TimeEntry;
import de.zeiterfassung.repo.TimeEntryRepository;
import de.zeiterfassung.service.AuditService;
import de.zeiterfassung.service.BusinessRuleException;
import de.zeiterfassung.service.EntryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 18);

    private TimeEntryRepository repo;
    private EntryService service;
    private final AppUser user = new AppUser("Mia", "mia@example.test", "x", Role.EMPLOYEE, null);

    @BeforeEach
    void setUp() {
        repo = mock(TimeEntryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2025-06-18T10:00:00Z"), ZoneOffset.UTC);
        service = new EntryService(repo, mock(AuditService.class), clock);
        when(repo.findOverlapping(any(), any(), any(), any(), anyLong())).thenReturn(List.of());
        when(repo.findByUserAndWorkDate(any(), any())).thenReturn(List.of());
    }

    @Test
    void validBooking_hasNoWarnings() {
        assertThat(service.validate(user, null, TODAY, LocalTime.of(8, 0), LocalTime.of(16, 30), 30)).isEmpty();
    }

    @Test
    void endBeforeStart_isRejected() {
        assertThatThrownBy(() -> service.validate(user, null, TODAY, LocalTime.of(16, 0), LocalTime.of(8, 0), 0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Ende");
    }

    @Test
    void endEqualsStart_isRejected() {
        assertThatThrownBy(() -> service.validate(user, null, TODAY, LocalTime.of(8, 0), LocalTime.of(8, 0), 0))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void futureDate_isRejected() {
        assertThatThrownBy(() -> service.validate(user, null, TODAY.plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0), 0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Zukunft");
    }

    @Test
    void breakLongerThanWork_isRejected() {
        assertThatThrownBy(() -> service.validate(user, null, TODAY, LocalTime.of(8, 0), LocalTime.of(9, 0), 61))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Pause");
    }

    @Test
    void overlap_isRejected() {
        when(repo.findOverlapping(any(), any(), any(), any(), anyLong()))
                .thenReturn(List.of(mock(TimeEntry.class)));
        assertThatThrownBy(() -> service.validate(user, null, TODAY, LocalTime.of(8, 0), LocalTime.of(9, 0), 0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("überschneidet");
    }

    @Test
    void veryLongDay_producesWarningButIsAllowed() {
        List<String> warnings = service.validate(user, null, TODAY, LocalTime.of(6, 0), LocalTime.of(18, 0), 0);
        assertThat(warnings).hasSize(1).first().asString().contains("10 Stunden");
    }

    @Test
    void exactlyTenHours_isNotYetWarned() {
        assertThat(service.validate(user, null, TODAY, LocalTime.of(6, 0), LocalTime.of(16, 0), 0)).isEmpty();
    }
}
