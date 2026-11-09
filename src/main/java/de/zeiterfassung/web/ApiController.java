package de.zeiterfassung.web;

import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.service.*;
import de.zeiterfassung.service.Dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Zeiterfassung")
public class ApiController {

    private final CurrentUserService currentUser;
    private final UserService userService;
    private final EntryService entryService;
    private final CorrectionService correctionService;
    private final SummaryService summaryService;
    private final ExportService exportService;
    private final Clock clock;

    public ApiController(CurrentUserService currentUser, UserService userService, EntryService entryService,
                         CorrectionService correctionService, SummaryService summaryService,
                         ExportService exportService, Clock clock) {
        this.currentUser = currentUser;
        this.userService = userService;
        this.entryService = entryService;
        this.correctionService = correctionService;
        this.summaryService = summaryService;
        this.exportService = exportService;
        this.clock = clock;
    }

    // ---------- Benutzer ----------

    @GetMapping("/me")
    @Operation(summary = "Angemeldeter Benutzer")
    public UserResponse me(Authentication auth) {
        return userService.me(currentUser.require(auth));
    }

    // ---------- Buchungen ----------

    @GetMapping("/entries")
    @Operation(summary = "Eigene Buchungen im Zeitraum (Standard: aktueller Monat)")
    public List<EntryResponse> entries(Authentication auth,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        YearMonth now = YearMonth.now(clock.withZone(WorkTime.ZONE));
        LocalDate start = from != null ? from : now.atDay(1);
        LocalDate end = to != null ? to : now.atEndOfMonth();
        return entryService.list(currentUser.require(auth), start, end);
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Zeit buchen (mit Plausibilitätsprüfung; Warnungen in der Antwort)")
    public EntryResult create(Authentication auth, @Valid @RequestBody EntryRequest request) {
        return entryService.create(currentUser.require(auth), request);
    }

    // ---------- Korrekturen ----------

    @PostMapping("/entries/{id}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Korrekturantrag für eine eigene Buchung stellen")
    public CorrectionResponse requestCorrection(Authentication auth, @PathVariable long id,
                                                @Valid @RequestBody CorrectionRequestDto dto) {
        return correctionService.request(currentUser.require(auth), id, dto);
    }

    @GetMapping("/corrections/mine")
    @Operation(summary = "Eigene Korrekturanträge")
    public List<CorrectionResponse> myCorrections(Authentication auth) {
        return correctionService.mine(currentUser.require(auth));
    }

    @GetMapping("/corrections/pending")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Offene Korrekturanträge des Teams")
    public List<CorrectionResponse> pending(Authentication auth) {
        return correctionService.pendingFor(currentUser.require(auth));
    }

    @PostMapping("/corrections/{id}/decision")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Korrekturantrag genehmigen oder ablehnen")
    public CorrectionResponse decide(Authentication auth, @PathVariable long id,
                                     @Valid @RequestBody DecisionRequest decision) {
        return correctionService.decide(currentUser.require(auth), id, decision);
    }

    // ---------- Auswertung und Export ----------

    @GetMapping("/summary")
    @Operation(summary = "Monatsübersicht mit Summen und Saldo")
    public SummaryResponse summary(Authentication auth, @RequestParam(required = false) String month) {
        return summaryService.month(currentUser.require(auth), parseMonth(month));
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Monatsübersicht als CSV")
    public ResponseEntity<byte[]> csv(Authentication auth, @RequestParam(required = false) String month) {
        YearMonth ym = parseMonth(month);
        return download(exportService.csv(currentUser.require(auth), ym),
                new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8), "zeiterfassung-" + ym + ".csv");
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Monatsübersicht als PDF")
    public ResponseEntity<byte[]> pdf(Authentication auth, @RequestParam(required = false) String month) {
        YearMonth ym = parseMonth(month);
        return download(exportService.pdf(currentUser.require(auth), ym), MediaType.APPLICATION_PDF,
                "zeiterfassung-" + ym + ".pdf");
    }

    // ---------- Team (Vorgesetzte) ----------

    @GetMapping("/team/members")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Mitglieder des eigenen Teams")
    public List<UserResponse> teamMembers(Authentication auth) {
        return userService.teamOf(currentUser.require(auth));
    }

    @GetMapping("/team/{userId}/entries")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Buchungen eines Teammitglieds (nur Lesezugriff)")
    public List<EntryResponse> teamEntries(Authentication auth, @PathVariable long userId,
                                           @RequestParam(required = false) String month) {
        AppUser member = userService.teamMember(currentUser.require(auth), userId);
        YearMonth ym = parseMonth(month);
        return entryService.list(member, ym.atDay(1), ym.atEndOfMonth());
    }

    // ---------- Administration ----------

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Alle Benutzer")
    public List<UserResponse> users() {
        return userService.all();
    }

    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Benutzer anlegen")
    public UserResponse createUser(Authentication auth, @Valid @RequestBody CreateUserRequest request) {
        return userService.create(currentUser.require(auth), request);
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Änderungsprotokoll (neueste zuerst)")
    public List<AuditResponse> audit(@RequestParam(defaultValue = "100") int limit) {
        return userService.auditTrail(limit);
    }

    // ---------- Hilfsmethoden ----------

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(clock.withZone(WorkTime.ZONE));
        }
        try {
            return YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BusinessRuleException("Der Monat muss im Format JJJJ-MM angegeben werden.");
        }
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
