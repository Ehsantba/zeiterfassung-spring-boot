package de.zeiterfassung.service;

import de.zeiterfassung.domain.*;
import de.zeiterfassung.repo.CorrectionRequestRepository;
import de.zeiterfassung.repo.TimeEntryRepository;
import de.zeiterfassung.service.Dto.CorrectionRequestDto;
import de.zeiterfassung.service.Dto.CorrectionResponse;
import de.zeiterfassung.service.Dto.DecisionRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buchungen werden nie still überschrieben: Der Mitarbeiter stellt einen Korrekturantrag,
 * der Vorgesetzte entscheidet, und jeder Schritt landet im Änderungsprotokoll.
 */
@Service
@Transactional
public class CorrectionService {

    private final TimeEntryRepository entries;
    private final CorrectionRequestRepository corrections;
    private final EntryService entryService;
    private final AuditService audit;
    private final Clock clock;

    public CorrectionService(TimeEntryRepository entries, CorrectionRequestRepository corrections,
                             EntryService entryService, AuditService audit, Clock clock) {
        this.entries = entries;
        this.corrections = corrections;
        this.entryService = entryService;
        this.audit = audit;
        this.clock = clock;
    }

    public CorrectionResponse request(AppUser user, long entryId, CorrectionRequestDto dto) {
        TimeEntry entry = entries.findById(entryId)
                .filter(e -> e.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Buchung nicht gefunden."));
        if (entry.getStatus() == EntryStatus.CORRECTION_PENDING) {
            throw new ConflictException("Für diese Buchung liegt bereits ein offener Korrekturantrag vor.");
        }
        entryService.validate(user, entry.getId(), entry.getWorkDate(), dto.newStartTime(), dto.newEndTime(),
                dto.newBreakMinutes());

        CorrectionRequest request = new CorrectionRequest(entry, user, dto.newStartTime(), dto.newEndTime(),
                dto.newBreakMinutes(), EntryService.clean(dto.newNote()), dto.reason().strip(), Instant.now(clock));
        corrections.save(request);
        entry.markCorrectionPending();
        audit.log("CorrectionRequest", request.getId(), "REQUEST", user.getEmail(),
                AuditService.describe(entry),
                AuditService.describe(entry.getWorkDate().toString(), dto.newStartTime(), dto.newEndTime(),
                        dto.newBreakMinutes(), dto.newNote()) + " | Grund: " + request.getReason());
        return CorrectionResponse.of(request);
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> mine(AppUser user) {
        return corrections.findByRequestedByOrderByCreatedAtDesc(user).stream().map(CorrectionResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> pendingFor(AppUser actor) {
        List<CorrectionRequest> list = actor.getRole() == Role.ADMIN
                ? corrections.findByStatusOrderByCreatedAtAsc(CorrectionStatus.PENDING)
                : corrections.findByStatusForSupervisor(CorrectionStatus.PENDING, actor);
        return list.stream().map(CorrectionResponse::of).toList();
    }

    public CorrectionResponse decide(AppUser actor, long correctionId, DecisionRequest decision) {
        CorrectionRequest request = corrections.findById(correctionId)
                .orElseThrow(() -> new NotFoundException("Korrekturantrag nicht gefunden."));
        AppUser requester = request.getRequestedBy();
        boolean allowed = actor.getRole() == Role.ADMIN
                || (requester.getSupervisor() != null && requester.getSupervisor().getId().equals(actor.getId()));
        if (!allowed) {
            // Gleiche Antwort wie "nicht gefunden", damit fremde Anträge nicht erkennbar sind.
            throw new NotFoundException("Korrekturantrag nicht gefunden.");
        }
        if (actor.getId().equals(requester.getId())) {
            throw new ForbiddenOperationException("Eigene Korrekturanträge dürfen nicht selbst entschieden werden.");
        }
        if (request.getStatus() != CorrectionStatus.PENDING) {
            throw new ConflictException("Der Antrag wurde bereits entschieden.");
        }
        String comment = EntryService.clean(decision.comment());
        if (!decision.approve() && comment == null) {
            throw new BusinessRuleException("Bei einer Ablehnung ist ein Kommentar erforderlich.");
        }

        TimeEntry entry = request.getEntry();
        String before = AuditService.describe(entry);
        if (decision.approve()) {
            // Erneute Prüfung: Zwischenzeitlich könnte eine andere Buchung entstanden sein.
            entryService.validate(entry.getUser(), entry.getId(), entry.getWorkDate(), request.getNewStartTime(),
                    request.getNewEndTime(), request.getNewBreakMinutes());
            entry.applyCorrection(request.getNewStartTime(), request.getNewEndTime(),
                    request.getNewBreakMinutes(), request.getNewNote());
        } else {
            entry.markBooked();
        }
        request.decide(decision.approve(), actor, comment, Instant.now(clock));
        entries.save(entry);

        audit.log("CorrectionRequest", request.getId(), decision.approve() ? "APPROVE" : "REJECT", actor.getEmail(),
                before, AuditService.describe(entry) + (comment == null ? "" : " | Kommentar: " + comment));
        return CorrectionResponse.of(request);
    }
}
