package de.zeiterfassung.service;

import de.zeiterfassung.domain.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Alle Ein- und Ausgabeobjekte der API. Entities verlassen die Service-Schicht nie. */
public final class Dto {

    private Dto() {
    }

    public record EntryRequest(
            @NotNull LocalDate workDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Min(0) @Max(1440) int breakMinutes,
            @Size(max = 500) String note) {
    }

    public record CorrectionRequestDto(
            @NotNull LocalTime newStartTime,
            @NotNull LocalTime newEndTime,
            @Min(0) @Max(1440) int newBreakMinutes,
            @Size(max = 500) String newNote,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record DecisionRequest(boolean approve, @Size(max = 500) String comment) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotNull Role role,
            Long supervisorId) {
    }

    public record EntryResponse(Long id, LocalDate workDate, LocalTime startTime, LocalTime endTime,
                                int breakMinutes, long netMinutes, String note, EntryStatus status) {
        public static EntryResponse of(TimeEntry e) {
            return new EntryResponse(e.getId(), e.getWorkDate(), e.getStartTime(), e.getEndTime(),
                    e.getBreakMinutes(),
                    WorkTime.netMinutes(e.getWorkDate(), e.getStartTime(), e.getEndTime(), e.getBreakMinutes()),
                    e.getNote(), e.getStatus());
        }
    }

    public record EntryResult(EntryResponse entry, List<String> warnings) {
    }

    public record CorrectionResponse(Long id, Long entryId, LocalDate workDate, String requestedBy,
                                     LocalTime oldStartTime, LocalTime oldEndTime, int oldBreakMinutes,
                                     LocalTime newStartTime, LocalTime newEndTime, int newBreakMinutes,
                                     String newNote, String reason, CorrectionStatus status,
                                     String decidedBy, String decisionComment, Instant createdAt) {
        public static CorrectionResponse of(CorrectionRequest c) {
            TimeEntry e = c.getEntry();
            return new CorrectionResponse(c.getId(), e.getId(), e.getWorkDate(), c.getRequestedBy().getName(),
                    e.getStartTime(), e.getEndTime(), e.getBreakMinutes(),
                    c.getNewStartTime(), c.getNewEndTime(), c.getNewBreakMinutes(), c.getNewNote(),
                    c.getReason(), c.getStatus(),
                    c.getDecidedBy() == null ? null : c.getDecidedBy().getName(),
                    c.getDecisionComment(), c.getCreatedAt());
        }
    }

    public record DaySummary(LocalDate date, long workedMinutes, long targetMinutes, int entries) {
    }

    public record SummaryResponse(String month, List<DaySummary> days, long workedMinutes,
                                  long targetMinutes, long balanceMinutes) {
    }

    public record UserResponse(Long id, String name, String email, Role role, String supervisor, boolean active) {
        public static UserResponse of(AppUser u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                    u.getSupervisor() == null ? null : u.getSupervisor().getName(), u.isActive());
        }
    }

    public record AuditResponse(Long id, String objectType, Long objectId, String action, String actor,
                                Instant occurredAt, String oldValue, String newValue) {
        public static AuditResponse of(AuditLog a) {
            return new AuditResponse(a.getId(), a.getObjectType(), a.getObjectId(), a.getAction(), a.getActor(),
                    a.getOccurredAt(), a.getOldValue(), a.getNewValue());
        }
    }
}
