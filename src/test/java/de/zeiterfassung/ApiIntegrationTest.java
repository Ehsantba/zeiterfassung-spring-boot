package de.zeiterfassung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.Role;
import de.zeiterfassung.domain.TimeEntry;
import de.zeiterfassung.repo.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AppUserRepository users;
    @Autowired TimeEntryRepository entries;
    @Autowired CorrectionRequestRepository corrections;
    @Autowired AuditLogRepository audit;
    @Autowired TransactionTemplate tx;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    AppUser admin, chef, otherChef, mia, ben, outsider;

    @BeforeEach
    void data() {
        audit.deleteAll();
        corrections.deleteAll();
        entries.deleteAll();
        jdbc.update("update app_user set supervisor_id = null");
        users.deleteAll();
        String hash = encoder.encode("geheim-passwort-1");
        admin = users.save(new AppUser("Alex Admin", "admin@example.test", hash, Role.ADMIN, null));
        chef = users.save(new AppUser("Sam Chef", "chef@example.test", hash, Role.SUPERVISOR, admin));
        otherChef = users.save(new AppUser("Olli Anders", "other@example.test", hash, Role.SUPERVISOR, admin));
        mia = users.save(new AppUser("Mia Muster", "mia@example.test", hash, Role.EMPLOYEE, chef));
        ben = users.save(new AppUser("Ben Beispiel", "ben@example.test", hash, Role.EMPLOYEE, chef));
        outsider = users.save(new AppUser("Otto Fremd", "otto@example.test", hash, Role.EMPLOYEE, otherChef));
    }

    static RequestPostProcessor as(String email, String role) {
        return user(email).roles(role);
    }

    String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }

    long book(String email, String date, String start, String end, int pause) throws Exception {
        String res = mvc.perform(post("/api/entries").with(as(email, "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workDate":"%s","startTime":"%s","endTime":"%s","breakMinutes":%d,"note":"Test"}
                                """.formatted(date, start, end, pause)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("entry").get("id").asLong();
    }

    // ---------- Authentifizierung und CSRF ----------

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/entries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void writingWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/api/entries").with(as("mia@example.test", "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"2025-06-17\",\"startTime\":\"08:00\",\"endTime\":\"09:00\",\"breakMinutes\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginWithCorrectAndWrongPassword() throws Exception {
        mvc.perform(post("/api/login").with(csrf()).param("username", "mia@example.test")
                        .param("password", "geheim-passwort-1"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/login").with(csrf()).param("username", "mia@example.test")
                        .param("password", "falsch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() {
        assertThat(users.findByEmailIgnoreCase("mia@example.test").orElseThrow().getPasswordHash())
                .startsWith("$2").doesNotContain("geheim");
    }

    // ---------- Buchungen ----------

    @Test
    void bookingIsListedAndSummarised() throws Exception {
        book("mia@example.test", "2025-06-16", "08:00", "16:30", 30); // Montag, 8 Std netto
        book("mia@example.test", "2025-06-17", "08:00", "17:30", 30); // Dienstag, 9 Std netto

        mvc.perform(get("/api/entries").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));

        // Mo 16.06 und Di 17.06 zählen als Soll; heute (Mi 18.06) hat keine Buchung und zählt nicht.
        // Vor dem 16.06. gibt es 9 weitere Werktage im Juni (2.-6., 9.-13.) = 10 Tage + 16. + 17. = 12 Tage Soll.
        mvc.perform(get("/api/summary").param("month", "2025-06").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workedMinutes").value(17 * 60))
                .andExpect(jsonPath("$.targetMinutes").value(12 * 8 * 60))
                .andExpect(jsonPath("$.balanceMinutes").value(17 * 60 - 12 * 8 * 60));
    }

    @Test
    void usersOnlySeeTheirOwnEntries() throws Exception {
        book("mia@example.test", "2025-06-16", "08:00", "12:00", 0);
        mvc.perform(get("/api/entries").with(as("ben@example.test", "EMPLOYEE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void overlappingBookingIsRejectedWith422() throws Exception {
        book("mia@example.test", "2025-06-16", "08:00", "12:00", 0);
        mvc.perform(post("/api/entries").with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"2025-06-16\",\"startTime\":\"11:00\",\"endTime\":\"13:00\",\"breakMinutes\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("überschneidet")));
    }

    @Test
    void invalidInputIsRejectedWith400() throws Exception {
        mvc.perform(post("/api/entries").with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":null,\"startTime\":\"08:00\",\"endTime\":\"09:00\",\"breakMinutes\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void longDayReturnsWarning() throws Exception {
        mvc.perform(post("/api/entries").with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workDate\":\"2025-06-16\",\"startTime\":\"06:00\",\"endTime\":\"18:00\",\"breakMinutes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings", hasSize(1)));
    }

    // ---------- Korrekturen ----------

    @Test
    void correctionFlow_approveAppliesChangeAndIsAudited() throws Exception {
        long id = book("mia@example.test", "2025-06-16", "08:00", "16:00", 30);

        String res = mvc.perform(post("/api/entries/" + id + "/corrections")
                        .with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStartTime\":\"07:30\",\"newEndTime\":\"16:00\",\"newBreakMinutes\":30,\"reason\":\"Früher angefangen\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long correctionId = json.readTree(res).get("id").asLong();

        // Zweiter Antrag auf dieselbe Buchung ist nicht möglich.
        mvc.perform(post("/api/entries/" + id + "/corrections")
                        .with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStartTime\":\"07:00\",\"newEndTime\":\"16:00\",\"newBreakMinutes\":30,\"reason\":\"nochmal\"}"))
                .andExpect(status().isConflict());

        // Der Vorgesetzte sieht den Antrag, ein fremder Vorgesetzter nicht.
        mvc.perform(get("/api/corrections/pending").with(as("chef@example.test", "SUPERVISOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/corrections/pending").with(as("other@example.test", "SUPERVISOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        // Fremder Vorgesetzter und Mitarbeiter dürfen nicht entscheiden.
        mvc.perform(post("/api/corrections/" + correctionId + "/decision")
                        .with(as("other@example.test", "SUPERVISOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/corrections/" + correctionId + "/decision")
                        .with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/corrections/" + correctionId + "/decision")
                        .with(as("chef@example.test", "SUPERVISOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"comment\":\"ok\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        TimeEntry e = entries.findById(id).orElseThrow();
        assertThat(e.getStartTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(e.getStatus().name()).isEqualTo("BOOKED");

        // Erneute Entscheidung ist nicht möglich.
        mvc.perform(post("/api/corrections/" + correctionId + "/decision")
                        .with(as("chef@example.test", "SUPERVISOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false,\"comment\":\"x\"}"))
                .andExpect(status().isConflict());

        // Änderungsprotokoll: Buchung, Antrag, Genehmigung.
        String trail = mvc.perform(get("/api/admin/audit").with(as("admin@example.test", "ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode nodes = json.readTree(trail);
        assertThat(nodes).extracting(n -> n.get("action").asText()).contains("CREATE", "REQUEST", "APPROVE");
    }

    @Test
    void rejectionRequiresComment_andKeepsOriginalValues() throws Exception {
        long id = book("mia@example.test", "2025-06-16", "08:00", "16:00", 30);
        String res = mvc.perform(post("/api/entries/" + id + "/corrections")
                        .with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStartTime\":\"06:00\",\"newEndTime\":\"16:00\",\"newBreakMinutes\":30,\"reason\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long cid = json.readTree(res).get("id").asLong();

        mvc.perform(post("/api/corrections/" + cid + "/decision").with(as("chef@example.test", "SUPERVISOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false}"))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/corrections/" + cid + "/decision").with(as("chef@example.test", "SUPERVISOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false,\"comment\":\"Nicht belegt\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(entries.findById(id).orElseThrow().getStartTime()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void cannotRequestCorrectionForSomeoneElsesEntry() throws Exception {
        long id = book("mia@example.test", "2025-06-16", "08:00", "16:00", 30);
        mvc.perform(post("/api/entries/" + id + "/corrections")
                        .with(as("ben@example.test", "EMPLOYEE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStartTime\":\"07:00\",\"newEndTime\":\"16:00\",\"newBreakMinutes\":30,\"reason\":\"Fremd\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- Rollen ----------

    @Test
    void roleBasedAccess() throws Exception {
        mvc.perform(get("/api/admin/users").with(as("mia@example.test", "EMPLOYEE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").with(as("chef@example.test", "SUPERVISOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/corrections/pending").with(as("mia@example.test", "EMPLOYEE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(as("admin@example.test", "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void supervisorSeesOnlyOwnTeam() throws Exception {
        book("mia@example.test", "2025-06-16", "08:00", "12:00", 0);
        mvc.perform(get("/api/team/" + mia.getId() + "/entries").param("month", "2025-06")
                        .with(as("chef@example.test", "SUPERVISOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/team/" + mia.getId() + "/entries").param("month", "2025-06")
                        .with(as("other@example.test", "SUPERVISOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanCreateUsers_duplicateEmailIsConflict() throws Exception {
        String payload = "{\"name\":\"Neu Nutzer\",\"email\":\"neu@example.test\",\"password\":\"mindestens-10-zeichen\",\"role\":\"EMPLOYEE\",\"supervisorId\":" + chef.getId() + "}";
        mvc.perform(post("/api/admin/users").with(as("admin@example.test", "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.supervisor").value("Sam Chef"));
        mvc.perform(post("/api/admin/users").with(as("admin@example.test", "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/users").with(as("admin@example.test", "ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@example.test\",\"password\":\"kurz\",\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- Export ----------

    @Test
    void csvAndPdfExport() throws Exception {
        book("mia@example.test", "2025-06-16", "08:00", "16:30", 30);
        mvc.perform(get("/api/export/csv").param("month", "2025-06").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Datum;Beginn;Ende;Pause (Min);Netto (Std);Notiz")))
                .andExpect(content().string(containsString("16.06.2025;08:00;16:30;30;8,00;\"Test\"")));
        byte[] pdf = mvc.perform(get("/api/export/pdf").param("month", "2025-06").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        mvc.perform(get("/api/export/csv").param("month", "kaputt").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------- Gleichzeitige Änderungen ----------

    @Test
    void optimisticLockingPreventsLostUpdates() throws Exception {
        long id = book("mia@example.test", "2025-06-16", "08:00", "16:00", 30);

        // Zwei Bearbeiter laden dieselbe Buchung (gleiche Version).
        TimeEntry stale = tx.execute(s -> entries.findById(id).orElseThrow());

        // Der erste speichert seine Änderung: Die Version in der Datenbank erhöht sich.
        tx.executeWithoutResult(s -> {
            TimeEntry e = entries.findById(id).orElseThrow();
            e.applyCorrection(LocalTime.of(7, 0), LocalTime.of(16, 0), 30, "A");
            entries.saveAndFlush(e);
        });

        // Der zweite speichert auf Basis der veralteten Version und wird abgewiesen.
        stale.applyCorrection(LocalTime.of(9, 0), LocalTime.of(16, 0), 30, "B");
        org.junit.jupiter.api.Assertions.assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> entries.saveAndFlush(stale));
        assertThat(entries.findById(id).orElseThrow().getStartTime()).isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
