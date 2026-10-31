package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Settings → Security → Audit log: an admin reads their own workspace's trail and its members' account security
 * events, phrased from an allowlist, and takes it as a CSV that is itself audited and capped.
 */
@IntegrationTest
class AuditLogIntegrationTest extends FlowTestSupport {

    private static final String LOG = "/api/v1/workspace/audit-events";

    @Autowired private JdbcTemplate jdbc;

    private String adminEmail;
    private String admin;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = "yara@" + domain;
        workspaceId = createWorkspace(verifiedUser("Yara Haddad", adminEmail), "Haddad Audit Search");
        admin = login(adminEmail);
    }

    @Test
    @DisplayName("an admin reads the log, a member is refused and a pure client is not told it exists")
    void onlyAnAdminReadsIt() throws Exception {
        String memberEmail = "mira@" + domain;
        inviteAndAccept(admin, "Mira Saleh", memberEmail, "MEMBER");
        String member = login(memberEmail);
        String client = clientRepresentative(admin, "Omar Client", "omar@" + domain);

        for (String route : List.of(LOG, LOG + "/people", LOG + "/export")) {
            mvc.perform(get(route).header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
            mvc.perform(get(route).header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
            mvc.perform(get(route).header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("another workspace's events never show, and every line names its actor from the roster")
    void showsOnlyThisWorkspace() throws Exception {
        String rivalEmail = "rita@" + domain;
        String rivalWorkspace = createWorkspace(verifiedUser("Rita Rival", rivalEmail), "Rival Audit Partners");
        login(rivalEmail);

        JsonNode entries = page(admin, "").get("entries");
        assertThat(entries).isNotEmpty();
        assertThat(entries.toString()).doesNotContain("Rita Rival");
        assertThat(typesOf(entries)).contains("WORKSPACE_CREATED", "LOGIN_SUCCEEDED");
        JsonNode created = first(entries, "WORKSPACE_CREATED");
        assertThat(created.get("actorName").asText()).isEqualTo("Yara Haddad");
        assertThat(created.get("actorKind").asText()).isEqualTo("MEMBER");
        assertThat(created.get("summary").asText()).isEqualTo("Created the workspace");
        assertThat(created.get("group").asText()).isEqualTo("membersAndAccess");
        assertThat(rivalWorkspace).isNotEqualTo(workspaceId);
    }

    @Test
    @DisplayName("a member's failed sign-in shows with its address; a stranger's and a sign-in elsewhere do not")
    void membersAccountEventsShow() throws Exception {
        String memberEmail = "mira@" + domain;
        inviteAndAccept(admin, "Mira Saleh", memberEmail, "MEMBER");
        String member = login(memberEmail);
        failLogin(memberEmail);
        failLogin("nobody@" + domain);

        JsonNode signIns = page(admin, "?group=signIn").get("entries");
        JsonNode failed = first(signIns, "LOGIN_FAILED");
        assertThat(failed.get("actorName").asText()).isEqualTo("Mira Saleh");
        assertThat(failed.get("outcome").asText()).isEqualTo("FAILURE");
        assertThat(failed.get("summary").asText()).isEqualTo("Sign-in failed: wrong password");
        assertThat(failed.get("ipAddress").asText()).isNotBlank();
        assertThat(countOf(signIns, "LOGIN_FAILED")).as("the stranger's failure belongs to no workspace").isEqualTo(1);
        long miraSignIns = signInsBy(signIns, "Mira Saleh");
        assertThat(miraSignIns).isEqualTo(1);

        // Mira founds a firm of her own; her next sign-in opens there, so it is that firm's line, not this one's.
        mvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"COMPANY","name":"Saleh Audit Advisory"}
                                """))
                .andExpect(status().isCreated());
        login(memberEmail);
        assertThat(signInsBy(page(admin, "?group=signIn").get("entries"), "Mira Saleh")).isEqualTo(miraSignIns);
    }

    @Test
    @DisplayName("a removed member still answers for what they did, and their account is no longer this firm's")
    void aRemovedMemberStillResolves() throws Exception {
        String memberEmail = "mira@" + domain;
        inviteAndAccept(admin, "Mira Saleh", memberEmail, "MEMBER");
        login(memberEmail);
        failLogin(memberEmail);

        mvc.perform(delete("/api/v1/members/" + memberIdOf(admin, memberEmail))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        failLogin(memberEmail);

        JsonNode entries = page(admin, "").get("entries");
        assertThat(countOf(entries, "LOGIN_FAILED")).as("only the failure while she was a member").isEqualTo(1);
        assertThat(first(entries, "LOGIN_FAILED").get("actorRemoved").asBoolean()).isTrue();
        assertThat(first(entries, "MEMBER_REMOVED").get("summary").asText())
                .isEqualTo("Removed Mira Saleh from the workspace");

        JsonNode people = body(mvc.perform(get(LOG + "/people").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        JsonNode mira = StreamSupport.stream(people.spliterator(), false)
                .filter(person -> person.get("name").asText().equals("Mira Saleh")).findFirst().orElseThrow();
        assertThat(mira.get("removed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a member removed and invited back shows nothing of their account from the time they were away")
    void aRejoinedMemberShowsNothingOfTheGap() throws Exception {
        String memberEmail = "mira@" + domain;
        inviteAndAccept(admin, "Mira Saleh", memberEmail, "MEMBER");
        String memberId = memberIdOf(admin, memberEmail);
        mvc.perform(delete("/api/v1/members/" + memberId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        failLogin(memberEmail);
        Long gapFailure = latestFailedLoginOf(memberEmail);

        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"MEMBER"}]
                                """.formatted(memberEmail)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + login(memberEmail))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(email.latestTokenFor(memberEmail))))
                .andExpect(status().isOk());
        failLogin(memberEmail);
        Long rejoinedFailure = latestFailedLoginOf(memberEmail);

        JsonNode failures = page(admin, "?group=signIn&outcome=FAILURE").get("entries");
        List<Long> shown = StreamSupport.stream(failures.spliterator(), false)
                .filter(entry -> entry.get("type").asText().equals("LOGIN_FAILED"))
                .map(entry -> entry.get("id").asLong())
                .toList();
        assertThat(shown).containsExactly(rejoinedFailure).doesNotContain(gapFailure);
    }

    @Test
    @DisplayName("no row's metadata reaches the page or the file, and a type nobody knows is a generic line")
    void metadataNeverLeaves() throws Exception {
        seed("MEMBER_INVITED", "invitation", UUID.randomUUID().toString(),
                "{\"email\":\"leak@elsewhere.example\",\"role\":\"ADMIN\"}");
        seed("CANDIDATE_REMOVED", "project", UUID.randomUUID().toString(), "{\"fullName\":\"Secret Person\"}");
        seed("SOMETHING_NEW", null, null, "{\"secret\":\"hush\"}");

        JsonNode page = page(admin, "");
        assertThat(page.toString()).doesNotContain("leak@elsewhere.example", "Secret Person", "hush");
        JsonNode entries = page.get("entries");
        assertThat(first(entries, "MEMBER_INVITED").get("summary").asText()).isEqualTo("Invited someone as an Admin");
        assertThat(first(entries, "CANDIDATE_REMOVED").get("summary").asText()).isEqualTo("Candidate removed");
        JsonNode unknown = first(entries, "SOMETHING_NEW");
        assertThat(unknown.get("summary").asText()).isEqualTo("Other activity");
        assertThat(unknown.get("group").isNull()).isTrue();
        List<String> fields = new ArrayList<>(unknown.propertyNames());
        assertThat(fields).containsExactlyInAnyOrder("id", "occurredAt", "type", "group", "outcome", "actorKind",
                "actorUserId", "actorName", "actorAvatarUrl", "actorRemoved", "summary", "positionTitle",
                "ipAddress");

        String csv = export(admin, "").getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("leak@elsewhere.example", "Secret Person", "hush");
        assertThat(csv).contains("Other activity");
    }

    @Test
    @DisplayName("filters narrow the read, an unknown category is refused, and the cursor pages")
    void filtersAndPages() throws Exception {
        failLogin(adminEmail);
        JsonNode failures = page(admin, "?outcome=FAILURE").get("entries");
        assertThat(failures).isNotEmpty();
        assertThat(failures).allSatisfy(entry -> assertThat(entry.get("outcome").asText()).isEqualTo("FAILURE"));

        JsonNode settings = page(admin, "?group=settings").get("entries");
        assertThat(typesOf(settings)).doesNotContain("WORKSPACE_CREATED", "LOGIN_SUCCEEDED");

        assertThat(page(admin, "?to=2000-01-01T00:00:00Z").get("entries")).isEmpty();
        assertThat(page(admin, "?actor=" + UUID.randomUUID()).get("entries")).isEmpty();

        MvcResult refused = mvc.perform(get(LOG + "?group=everything").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");

        JsonNode first = page(admin, "?limit=1");
        assertThat(first.get("entries")).hasSize(1);
        long cursor = first.get("nextCursor").asLong();
        JsonNode second = page(admin, "?limit=1&before=" + cursor);
        assertThat(second.get("entries").get(0).get("id").asLong()).isLessThan(cursor);
    }

    @Test
    @DisplayName("the export is a CSV of the same view, recorded once, and refused whole past its ceiling")
    void exportIsAuditedAndCapped() throws Exception {
        MvcResult file = export(admin, "?group=membersAndAccess");
        assertThat(file.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .startsWith("﻿Time (UTC),Person,Event,Category,Outcome,Position,IP address\r\n")
                .contains("Created the workspace");
        assertThat(exportLines()).isEqualTo(1);
        JsonNode exported = first(page(admin, "?group=exportsAndDownloads").get("entries"), "AUDIT_LOG_EXPORTED");
        assertThat(exported.get("summary").asText()).startsWith("Exported the audit log (");

        for (int row = 0; row < 16; row++) {
            seed("WORKSPACE_UPDATED", null, null, "{}");
        }
        MvcResult refused = mvc.perform(get(LOG + "/export").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
        assertThat(exportLines()).as("a refused export is not recorded as one").isEqualTo(1);
    }

    private JsonNode page(String token, String query) throws Exception {
        return body(mvc.perform(get(LOG + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private MvcResult export(String token, String query) throws Exception {
        return mvc.perform(get(LOG + "/export" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
    }

    private void failLogin(String emailAddress) throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"not-the-password"}
                                """.formatted(emailAddress)))
                .andExpect(status().isUnauthorized());
    }

    private void seed(String type, String targetType, String targetId, String metadata) {
        jdbc.update("insert into app_lm_audit_event (event_type, outcome, workspace_id, target_type, target_id, "
                        + "ip_address, metadata) values (?, 'SUCCESS', ?::uuid, ?, ?, '10.0.0.1', ?::jsonb)",
                type, workspaceId, targetType, targetId, metadata);
    }

    private Long latestFailedLoginOf(String emailAddress) {
        return jdbc.queryForObject("select max(e.id) from app_lm_audit_event e join app_lm_user u on u.id = e.actor_user_id "
                + "where u.email = ? and e.event_type = 'LOGIN_FAILED'", Long.class, emailAddress);
    }

    private Integer exportLines() {
        return jdbc.queryForObject("select count(*) from app_lm_audit_event where workspace_id = ?::uuid "
                + "and event_type = 'AUDIT_LOG_EXPORTED'", Integer.class, workspaceId);
    }

    private static Set<String> typesOf(JsonNode entries) {
        return StreamSupport.stream(entries.spliterator(), false)
                .map(entry -> entry.get("type").asText())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static long countOf(JsonNode entries, String type) {
        return StreamSupport.stream(entries.spliterator(), false)
                .filter(entry -> entry.get("type").asText().equals(type)).count();
    }

    private static long signInsBy(JsonNode entries, String name) {
        return StreamSupport.stream(entries.spliterator(), false)
                .filter(entry -> entry.get("type").asText().equals("LOGIN_SUCCEEDED"))
                .filter(entry -> name.equals(entry.get("actorName").asText())).count();
    }

    private static JsonNode first(JsonNode entries, String type) {
        return StreamSupport.stream(entries.spliterator(), false)
                .filter(entry -> entry.get("type").asText().equals(type))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " in " + entries));
    }
}
