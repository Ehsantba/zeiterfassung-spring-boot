# Architekturentscheidungen

Dieses Dokument erklärt, warum die Anwendung so gebaut ist. Die Verweise zeigen auf die Stellen im Code.

## 1. Schichtenarchitektur und DTOs
**Entscheidung:** Controller, Service, Repository; nach außen nur DTOs (`Dto.java`).
**Warum:** Jede Schicht hat genau eine Aufgabe. Regeln stehen an einer Stelle (Service) und gelten für jeden Zugriffsweg.
Entities nach außen zu geben würde interne Felder verraten (z. B. `passwordHash`), erzeugt Probleme mit Lazy Loading und koppelt die API an das Datenbankschema.

## 2. Passwörter und Rollen
- Passwörter: `BCryptPasswordEncoder` (`SecurityConfig`). BCrypt ist absichtlich langsam und salzt jeden Hash selbst.
- Rollen: Zwei Ebenen. `@PreAuthorize` an den Endpunkten (`ApiController`) und zusätzlich die Zugehörigkeitsprüfung in den Services (`CorrectionService.decide`, `UserService.teamMember`).
  Die zweite Ebene schützt davor, dass ein Vorgesetzter Daten fremder Teams sieht, obwohl er die Rolle hat.
- Fremde Objekte liefern „nicht gefunden“ statt „verboten“, damit man ihre Existenz nicht erraten kann.
- CSRF: Cookie-basiertes Token, das das Frontend als Header zurückschickt (`SecurityConfig`).

## 3. Gleichzeitige Änderungen
`@Version` an `TimeEntry` und `CorrectionRequest` (optimistisches Locking). Wer eine veraltete Version speichern will,
bekommt eine `ObjectOptimisticLockingFailureException`, die als HTTP 409 mit verständlicher Meldung zurückgegeben wird (`ApiExceptionHandler`).
Pessimistisches Sperren wäre hier unnötig, weil Konflikte selten sind und sich ohne Wartezeiten lösen lassen.
Test: `ApiIntegrationTest.optimisticLockingPreventsLostUpdates`.

## 4. Zeitumstellung
Die Dauer wird über `ZonedDateTime` in `Europe/Berlin` berechnet (`WorkTime`), nicht über Uhrzeit-Differenzen.
Am 30.03.2025 sind 01:00 bis 04:00 real nur 2 Stunden, am 26.10.2025 sind es 4 Stunden. Tests: `WorkTimeTest`.
Zeitstempel in der Datenbank (`created_at`, `occurred_at`) sind UTC-Instants.
Die Zeitquelle ist eine injizierbare `Clock`, dadurch sind Tests mit festem Datum möglich.

## 5. Flyway statt automatischer Schema-Erzeugung
`ddl-auto: validate` heißt: Hibernate ändert die Datenbank nie, es prüft nur, ob das Schema zum Code passt.
Das Schema wird mit Flyway versioniert (`V1__schema.sql`). Vorteile: reproduzierbare Datenbanken in jeder Umgebung,
nachvollziehbare Änderungen im Git-Verlauf, kontrollierte Migrationen ohne Datenverlust. Automatische Erzeugung ist für Produktion zu riskant.

## 6. Nachvollziehbarkeit
Buchungen werden nicht überschrieben. Änderungen laufen über `CorrectionRequest` und landen im `audit_log` (`AuditService`),
in derselben Transaktion (`Propagation.MANDATORY`): Entweder die Änderung und der Protokolleintrag werden beide gespeichert, oder keines von beiden.
Das Protokoll hat keine Setter und keine Änderungs- oder Löschpfade in der Anwendung.

## 7. Frontend: React statt Thymeleaf
React liefert eine reaktionsschnelle Oberfläche über dieselbe dokumentierte REST-API, die auch andere Clients nutzen könnten.
Das Build wird als statische Dateien von Spring Boot ausgeliefert, damit es nur einen Dienst gibt und kein CORS nötig ist.
Nachteil: ein zusätzlicher Build-Schritt (im Dockerfile und in der CI abgedeckt).

# Typische Fragen und wo die Antwort im Code steht

| Frage | Antwort in Kurzform | Stelle |
|---|---|---|
| Warum Schichtenarchitektur und DTOs? | Trennung der Aufgaben, keine internen Felder nach außen | Abschnitt 1, `Dto.java` |
| Wie werden Passwörter gespeichert, wie sind Rollen geschützt? | BCrypt; `@PreAuthorize` plus Zugehörigkeitsprüfung im Service | Abschnitt 2 |
| Gleichzeitige Änderungen, Zeitumstellung? | `@Version`; `ZonedDateTime` | Abschnitte 3 und 4 |
| Warum Flyway? | Versionierte, reproduzierbare Schemaänderungen | Abschnitt 5 |
| Wie getestet, was zuerst verbessern? | Siehe README „Tests“. Zuerst: Testcontainers mit echter PostgreSQL, Feiertage und Abwesenheiten, Sperre gegen Brute-Force | README „Grenzen“ |
