package de.zeiterfassung.web;

import de.zeiterfassung.service.AbsenceService;
import de.zeiterfassung.service.CurrentUserService;
import de.zeiterfassung.service.Dto.*;
import de.zeiterfassung.service.WorkTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Abwesenheiten und Feiertage")
public class AbsenceController {

    private final AbsenceService service;
    private final CurrentUserService currentUser;
    private final Clock clock;

    public AbsenceController(AbsenceService service, CurrentUserService currentUser, Clock clock) {
        this.service = service;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/absences")
    @Operation(summary = "Eigene Abwesenheiten und Urlaubskonto eines Jahres")
    public AbsenceOverview overview(Authentication auth, @RequestParam(required = false) Integer year) {
        return service.overview(currentUser.require(auth), year != null ? year : currentYear());
    }

    @PostMapping("/absences")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abwesenheit eintragen (Urlaub braucht Genehmigung)")
    public AbsenceResponse create(Authentication auth, @Valid @RequestBody AbsenceRequest request) {
        return service.create(currentUser.require(auth), request);
    }

    @PostMapping("/absences/{id}/cancel")
    @Operation(summary = "Eigene Abwesenheit zurückziehen")
    public AbsenceResponse cancel(Authentication auth, @PathVariable long id) {
        return service.cancel(currentUser.require(auth), id);
    }

    @GetMapping("/absences/pending")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Offene Urlaubsanträge des Teams")
    public List<AbsenceResponse> pending(Authentication auth) {
        return service.pendingFor(currentUser.require(auth));
    }

    @PostMapping("/absences/{id}/decision")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Urlaubsantrag genehmigen oder ablehnen")
    public AbsenceResponse decide(Authentication auth, @PathVariable long id,
                                  @Valid @RequestBody DecisionRequest decision) {
        return service.decide(currentUser.require(auth), id, decision);
    }

    @GetMapping("/holidays")
    @Operation(summary = "Gesetzliche Feiertage in Nordrhein-Westfalen")
    public List<HolidayResponse> holidays(@RequestParam(required = false) Integer year) {
        return service.holidays(year != null ? year : currentYear());
    }

    private int currentYear() {
        return LocalDate.now(clock.withZone(WorkTime.ZONE)).getYear();
    }
}
