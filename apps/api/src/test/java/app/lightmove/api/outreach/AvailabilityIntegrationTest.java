package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.WeeklyHours;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Working hours and the daily email cap: the workspace's week read from its firm's country until an admin chooses
 * one, each consultant's own above it, the cap only ever lowered by a consultant, and who may change which.
 */
@IntegrationTest
class AvailabilityIntegrationTest extends FlowTestSupport {

    private static final String WORKSPACE = "/api/v1/workspace/availability";
    private static final String MINE = "/api/v1/me/availability";
    private static final String SUNDAY_TO_THURSDAY = """
            [{"day":"SUNDAY","start":"09:00","end":"17:00"},{"day":"MONDAY","start":"09:00","end":"17:00"},
             {"day":"TUESDAY","start":"09:00","end":"17:00"},{"day":"WEDNESDAY","start":"09:00","end":"17:00"},
             {"day":"THURSDAY","start":"09:00","end":"17:00"}]""";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private JdbcTemplate jdbc;

    private String admin;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        String adminEmail = "yara@" + domain;
        workspaceId = createWorkspace(verifiedUser("Yara Haddad", adminEmail), "Meridian Search");
        admin = login(adminEmail);
    }

    @Test
    @DisplayName("until an admin chooses, the week is Monday to Friday, or Sunday to Thursday for a Saudi firm")
    void theWeekFollowsTheFirmsCountry() throws Exception {
        JsonNode unknown = read(admin, WORKSPACE);
        assertThat(daysOf(unknown.get("weeklyHours"))).containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                "FRIDAY");
        assertThat(unknown.get("weeklyHours").get(0).get("start").asText()).startsWith("08:00");
        assertThat(unknown.get("weeklyHoursChosen").asBoolean()).isFalse();
        assertThat(unknown.get("suggestedFromCountry").isNull()).isTrue();
        assertThat(unknown.get("dailyEmailCap").asInt()).isEqualTo(50);
        assertThat(unknown.get("maxDailyEmailCap").asInt()).isEqualTo(200);

        jdbc.update("update app_lm_workspace set company_country = 'Saudi Arabia' where id = ?::uuid", workspaceId);
        JsonNode saudi = read(admin, WORKSPACE);
        assertThat(daysOf(saudi.get("weeklyHours"))).containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                "SUNDAY");
        assertThat(saudi.get("suggestedFromCountry").asText()).isEqualTo("Saudi Arabia");
    }

    @Test
    @DisplayName("an admin chooses the week and the cap; saving the same again records nothing more")
    void anAdminChoosesTheWeek() throws Exception {
        JsonNode saved = body(change(admin, WORKSPACE, SUNDAY_TO_THURSDAY, 80).andExpect(status().isOk()).andReturn());

        assertThat(daysOf(saved.get("weeklyHours"))).containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
                "SUNDAY");
        assertThat(saved.get("weeklyHoursChosen").asBoolean()).isTrue();
        assertThat(saved.get("suggestedFromCountry").isNull()).isTrue();
        assertThat(saved.get("dailyEmailCap").asInt()).isEqualTo(80);
        assertThat(saved.get("dailyEmailCapChosen").asBoolean()).isTrue();
        assertThat(auditLines()).isOne();

        change(admin, WORKSPACE, SUNDAY_TO_THURSDAY, 80).andExpect(status().isOk());
        assertThat(auditLines()).isOne();

        JsonNode back = body(change(admin, WORKSPACE, null, null).andExpect(status().isOk()).andReturn());
        assertThat(back.get("weeklyHoursChosen").asBoolean()).isFalse();
        assertThat(back.get("dailyEmailCap").asInt()).isEqualTo(50);
        assertThat(auditLines()).isEqualTo(2);
    }

    @Test
    @DisplayName("a week is refused off the half hour, backwards, twice over a day, or with no day worked")
    void anUnusableWeekIsRefused() throws Exception {
        for (String week : List.of(
                "[{\"day\":\"MONDAY\",\"start\":\"09:15\",\"end\":\"17:00\"}]",
                "[{\"day\":\"MONDAY\",\"start\":\"17:00\",\"end\":\"09:00\"}]",
                "[{\"day\":\"MONDAY\",\"start\":\"09:00\",\"end\":\"12:00\"},"
                        + "{\"day\":\"MONDAY\",\"start\":\"13:00\",\"end\":\"17:00\"}]",
                "[]")) {
            JsonNode problem = body(change(admin, WORKSPACE, week, null).andExpect(status().isBadRequest())
                    .andReturn());
            assertThat(problem.get("code").asText()).isEqualTo("VALIDATION_FAILED");
            assertThat(problem.get("fieldErrors").has("weeklyHours")).isTrue();
        }
        change(admin, WORKSPACE, "[{\"day\":\"MONDAY\",\"start\":\"09:00\",\"end\":\"23:59\"}]", null)
                .andExpect(status().isOk());
        for (int cap : new int[] {0, 201}) {
            JsonNode problem = body(change(admin, WORKSPACE, null, cap).andExpect(status().isBadRequest())
                    .andReturn());
            assertThat(problem.get("fieldErrors").has("dailyEmailCap")).isTrue();
        }
    }

    @Test
    @DisplayName("a consultant keeps their own week and a lower cap, and clears both back to the workspace's")
    void aConsultantKeepsTheirOwn() throws Exception {
        change(admin, WORKSPACE, null, 40).andExpect(status().isOk());
        String member = member();

        JsonNode inherited = read(member, MINE);
        assertThat(inherited.get("weeklyHours").isNull()).isTrue();
        assertThat(inherited.get("dailyEmailCap").isNull()).isTrue();
        assertThat(inherited.get("workspaceDailyEmailCap").asInt()).isEqualTo(40);
        assertThat(daysOf(inherited.get("workspaceWeeklyHours"))).hasSize(5);
        assertThat(inherited.get("timeZone").asText()).isNotBlank();
        assertThat(inherited.get("calendarStatesHours").asBoolean()).isFalse();

        assertThat(body(change(member, MINE, null, 41).andExpect(status().isBadRequest()).andReturn())
                .get("fieldErrors").has("dailyEmailCap")).isTrue();
        JsonNode own = body(change(member, MINE, "[{\"day\":\"SATURDAY\",\"start\":\"10:00\",\"end\":\"14:00\"}]", 20)
                .andExpect(status().isOk()).andReturn());
        assertThat(daysOf(own.get("weeklyHours"))).containsExactly("SATURDAY");
        assertThat(own.get("dailyEmailCap").asInt()).isEqualTo(20);
        assertThat(rowsOfMemberAvailability()).isOne();

        JsonNode cleared = body(change(member, MINE, null, null).andExpect(status().isOk()).andReturn());
        assertThat(cleared.get("weeklyHours").isNull()).isTrue();
        assertThat(rowsOfMemberAvailability()).isZero();
    }

    @Test
    @DisplayName("only an admin changes the workspace's; a pure client is told none of it exists")
    void whoMayChangeWhat() throws Exception {
        String member = member();
        read(member, WORKSPACE);
        change(member, WORKSPACE, SUNDAY_TO_THURSDAY, null).andExpect(status().isForbidden());

        String client = clientRepresentative(admin, "Nadia Saleh", "nadia@client-" + domain);
        for (MockHttpServletRequestBuilder route : List.of(get(WORKSPACE), get(MINE),
                put(MINE).contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            as(client, route).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("a mailbox sends up to its owner's cap, never above the workspace's, and says whose hours apply")
    void theMailboxCountsAgainstTheLowerCap() throws Exception {
        gateway.grant(new GrantedMailbox("grant-" + domain, "consultant@firm.example", "microsoft"));
        connectMailbox(admin);
        assertThat(dailyCapOfMailbox()).isEqualTo(50);

        change(admin, WORKSPACE, null, 30).andExpect(status().isOk());
        assertThat(dailyCapOfMailbox()).isEqualTo(30);
        change(admin, MINE, null, 10).andExpect(status().isOk());
        assertThat(dailyCapOfMailbox()).isEqualTo(10);

        gateway.calendarKeepsWorkingHours(WeeklyHours.uniform(Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0),
                LocalTime.of(17, 0)), ZoneId.of("Europe/London"));
        assertThat(read(admin, MINE).get("calendarStatesHours").asBoolean()).isTrue();
    }

    private int dailyCapOfMailbox() throws Exception {
        return read(admin, "/api/v1/outreach/mailbox").get("connection").get("dailyCap").asInt();
    }

    private String member() throws Exception {
        inviteAndAccept(admin, "Omar Saleh", "omar@" + domain, "MEMBER");
        return login("omar@" + domain);
    }

    private int auditLines() {
        return jdbc.queryForObject("select count(*) from app_lm_audit_event where workspace_id = ?::uuid "
                + "and event_type = 'WORKSPACE_UPDATED' and metadata ->> 'section' = 'availability'", Integer.class,
                workspaceId);
    }

    private int rowsOfMemberAvailability() {
        return jdbc.queryForObject("select count(*) from app_lm_member_availability where workspace_id = ?::uuid",
                Integer.class, workspaceId);
    }

    private static List<String> daysOf(JsonNode week) {
        List<String> days = new ArrayList<>();
        week.forEach(day -> days.add(day.get("day").asText()));
        return days;
    }

    private JsonNode read(String token, String path) throws Exception {
        return body(as(token, get(path)).andExpect(status().isOk()).andReturn());
    }

    private ResultActions change(String token, String path, String weeklyHours, Integer dailyEmailCap)
            throws Exception {
        return as(token, put(path).contentType(MediaType.APPLICATION_JSON).content("""
                {"weeklyHours":%s,"dailyEmailCap":%s}""".formatted(weeklyHours, dailyEmailCap)));
    }

    private void connectMailbox(String token) throws Exception {
        MvcResult started = as(token, post("/api/v1/outreach/mailbox/connect")
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"microsoft\"}"))
                .andExpect(status().isOk())
                .andReturn();
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get("/api/v1/outreach/mailbox/callback").param("state", state).param("code", "code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }
}
