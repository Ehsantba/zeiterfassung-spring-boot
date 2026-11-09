package de.zeiterfassung;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.zeiterfassung.domain.AppUser;
import de.zeiterfassung.domain.Role;
import de.zeiterfassung.repo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class AbsenceIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AppUserRepository users;
    @Autowired TimeEntryRepository entries;
    @Autowired CorrectionRequestRepository corrections;
    @Autowired AbsenceRepository absences;
    @Autowired AuditLogRepository audit;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void data() {
        audit.deleteAll();
        absences.deleteAll();
        corrections.deleteAll();
        entries.deleteAll();
        jdbc.update("update app_user set supervisor_id = null");
        users.deleteAll();
        String hash = encoder.encode("geheim-passwort-1");
        AppUser admin = users.save(new AppUser("Alex Admin", "admin@example.test", hash, Role.ADMIN, null));
        AppUser chef = users.save(new AppUser("Sam Chef", "chef@example.test", hash, Role.SUPERVISOR, admin));
        AppUser other = users.save(new AppUser("Olli Anders", "other@example.test", hash, Role.SUPERVISOR, admin));
        users.save(new AppUser("Mia Muster", "mia@example.test", hash, Role.EMPLOYEE, chef));
        users.save(new AppUser("Otto Fremd", "otto@example.test", hash, Role.EMPLOYEE, other));
    }

    static RequestPostProcessor as(String email, String role) {
        return user(email).roles(role);
    }

    ResultActions absence(String email, String type, String from, String to) throws Exception {
        return mvc.perform(post("/api/absences").with(as(email, "EMPLOYEE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"note\":\"Test\"}".formatted(type, from, to)));
    }

    long createdId(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void sickLeaveIsApprovedImmediately_andRemovesTheTarget() throws Exception {
        absence("mia@example.test", "SICK", "2025-06-16", "2025-06-17")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.workdays").value(2));

        // Soll bis Di 17.06.: 5 + 4 Werktage vor dem Pfingstmontag-Block, 16. und 17. sind krank => 9 Tage
        mvc.perform(get("/api/summary").param("month", "2025-06").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetMinutes").value(9 * 8 * 60))
                .andExpect(jsonPath("$.days[15].absence").value("SICK"))
                .andExpect(jsonPath("$.days[8].holiday").value("Pfingstmontag"));
    }

    @Test
    void vacationNeedsApproval_andOnlyTheOwnSupervisorMayDecide() throws Exception {
        long id = createdId(absence("mia@example.test", "VACATION", "2025-07-07", "2025-07-11")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")));

        mvc.perform(get("/api/absences").param("year", "2025").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(jsonPath("$.vacation.pending").value(5))
                .andExpect(jsonPath("$.vacation.approved").value(0))
                .andExpect(jsonPath("$.vacation.remaining").value(25));

        mvc.perform(get("/api/absences/pending").with(as("chef@example.test", "SUPERVISOR")))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/absences/pending").with(as("other@example.test", "SUPERVISOR")))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/absences/" + id + "/decision").with(as("other@example.test", "SUPERVISOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/absences/" + id + "/decision").with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true}")).andExpect(status().isForbidden());

        mvc.perform(post("/api/absences/" + id + "/decision").with(as("chef@example.test", "SUPERVISOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":true,\"comment\":\"Schönen Urlaub\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/absences").param("year", "2025").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(jsonPath("$.vacation.approved").value(5))
                .andExpect(jsonPath("$.vacation.remaining").value(25));
    }

    @Test
    void rejectionNeedsComment() throws Exception {
        long id = createdId(absence("mia@example.test", "VACATION", "2025-07-07", "2025-07-08").andExpect(status().isCreated()));
        mvc.perform(post("/api/absences/" + id + "/decision").with(as("chef@example.test", "SUPERVISOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/absences/" + id + "/decision").with(as("chef@example.test", "SUPERVISOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false,\"comment\":\"Projektphase\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        // Abgelehnter Urlaub blockiert den Zeitraum nicht und zählt nicht aufs Konto
        absence("mia@example.test", "VACATION", "2025-07-07", "2025-07-08").andExpect(status().isCreated());
    }

    @Test
    void vacationBeyondTheYearlyEntitlementIsRejected() throws Exception {
        // 01.08. bis 30.09.2025 sind 43 Werktage, der Anspruch beträgt 30
        absence("mia@example.test", "VACATION", "2025-08-01", "2025-09-30")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Nicht genug Urlaub")));
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        absence("mia@example.test", "OTHER", "2025-06-17", "2025-06-16").andExpect(status().isUnprocessableEntity());
        // nur Wochenende
        absence("mia@example.test", "OTHER", "2025-06-14", "2025-06-15")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("keinen Arbeitstag")));
        // nur Feiertag (Pfingstmontag)
        absence("mia@example.test", "OTHER", "2025-06-09", "2025-06-09").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void overlappingAbsencesAreRejected() throws Exception {
        absence("mia@example.test", "SICK", "2025-06-16", "2025-06-17").andExpect(status().isCreated());
        absence("mia@example.test", "OTHER", "2025-06-17", "2025-06-18")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("bereits eine Abwesenheit")));
    }

    @Test
    void absenceAndTimeBookingsExcludeEachOther() throws Exception {
        absence("mia@example.test", "SICK", "2025-06-16", "2025-06-16").andExpect(status().isCreated());
        mvc.perform(post("/api/entries").with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\":\"2025-06-16\",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"breakMinutes\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Abwesenheit")));

        mvc.perform(post("/api/entries").with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\":\"2025-06-17\",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"breakMinutes\":0}"))
                .andExpect(status().isCreated());
        absence("mia@example.test", "SICK", "2025-06-17", "2025-06-17")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Zeitbuchungen")));
    }

    @Test
    void bookingOnAHolidayWorksButWarns() throws Exception {
        mvc.perform(post("/api/entries").with(as("mia@example.test", "EMPLOYEE")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workDate\":\"2025-06-09\",\"startTime\":\"08:00\",\"endTime\":\"12:00\",\"breakMinutes\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings[0]").value(containsString("Pfingstmontag")));
    }

    @Test
    void cancelledAbsenceFreesTheRange_andIsAudited() throws Exception {
        long id = createdId(absence("mia@example.test", "SICK", "2025-06-20", "2025-06-20").andExpect(status().isCreated()));
        mvc.perform(post("/api/absences/" + id + "/cancel").with(as("otto@example.test", "EMPLOYEE")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/absences/" + id + "/cancel").with(as("mia@example.test", "EMPLOYEE")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/absences/" + id + "/cancel").with(as("mia@example.test", "EMPLOYEE")).with(csrf()))
                .andExpect(status().isConflict());
        absence("mia@example.test", "SICK", "2025-06-20", "2025-06-20").andExpect(status().isCreated());

        mvc.perform(get("/api/admin/audit").with(as("admin@example.test", "ADMIN")))
                .andExpect(jsonPath("$[?(@.action=='CANCEL')]", hasSize(1)));
    }

    @Test
    void holidayEndpointListsTheElevenHolidaysOfNrw() throws Exception {
        mvc.perform(get("/api/holidays").param("year", "2026").with(as("mia@example.test", "EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(11)))
                .andExpect(jsonPath("$[?(@.name=='Karfreitag')].date").value("2026-04-03"));
    }
}
