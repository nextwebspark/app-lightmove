package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * An executive hidden from the client is absent from every read a client seat can reach — the list, the drawer, the
 * photo, the Companies grid's filters and sort, the talent map, the report, the export, the projects list's counts and
 * a personal API key — whether staff hid them (V132), the firm restricted their processing, or they said no to being
 * shared or withdrew it (V246, #1488). A client seat sees exactly what it saw before while everyone is visible; staff,
 * and a workspace API key, still read them everywhere.
 */
@IntegrationTest
class ClientVisibilityTest extends PipelineFlowSupport {

    private String projectId;
    private String shown;
    private String hidden;
    private String client;
    private String researcher;

    @BeforeEach
    void aPositionWithOneHiddenExecutive() throws Exception {
        firm("Client Visibility Firm");
        projectId = mandate("Chief Financial Officer");
        String company = capture(projectId, "ACWA Power");
        shown = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Shown Person","locationCountry":"United Arab Emirates"}"""
                .formatted(company)).get("id").asText();
        hidden = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Hidden Person","locationCountry":"United Arab Emirates"}"""
                .formatted(company)).get("id").asText();
        client = clientOn(projectId);
        researcher = researcherOn(projectId, "sara");
    }

    @Test
    @DisplayName("while everyone is visible, a client seat reads every executive, as it always did")
    void everyoneVisibleByDefault() throws Exception {
        assertThat(read(client, candidatesUrl(projectId))).contains("Shown Person", "Hidden Person");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/talent-map")).contains("Hidden Person");
    }

    /** The ways someone leaves a client's view: staff's switch, the firm's restriction, the person's own no. */
    enum HiddenBy { STAFF, RESTRICTION, SHARE_DECLINED, SHARE_WITHDRAWN }

    @ParameterizedTest(name = "hidden by {0}")
    @EnumSource(HiddenBy.class)
    @DisplayName("a hidden executive is absent from every read a client seat reaches, and a 404 by id")
    void aHiddenExecutiveIsAbsentForAClient(HiddenBy by) throws Exception {
        hide(by, hidden);

        assertThat(read(client, candidatesUrl(projectId))).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(mvc.perform(get(candidatesUrl(projectId) + "/" + hidden).header("Authorization", "Bearer " + client))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get(candidatesUrl(projectId) + "/" + hidden + "/photo")
                .header("Authorization", "Bearer " + client)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(read(client, "/api/v1/projects/" + projectId + "/talent-map")).contains("Shown Person")
                .doesNotContain("Hidden Person");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/report"))
                .contains("\"executivesMapped\":1");
        assertThat(read(client, "/api/v1/projects/" + projectId + "/export/companies")).contains("Shown Person")
                .doesNotContain("Hidden Person");
    }

    @ParameterizedTest(name = "hidden by {0}")
    @EnumSource(HiddenBy.class)
    @DisplayName("staff and an API key still read a hidden executive everywhere, and the pipeline says why")
    void staffStillSeeThem(HiddenBy by) throws Exception {
        hide(by, hidden);

        JsonNode row = pipelineRow(hidden);
        assertThat(row.get("clientHiddenReason").isNull() ? null : row.get("clientHiddenReason").asText())
                .isEqualTo(switch (by) {
                    case STAFF -> null;
                    case RESTRICTION -> "processing_restricted";
                    case SHARE_DECLINED -> "share_declined";
                    case SHARE_WITHDRAWN -> "share_withdrawn";
                });
        assertThat(row.get("clientVisible").asBoolean()).isEqualTo(by != HiddenBy.STAFF);
        for (String staff : new String[] {admin, researcher}) {
            assertThat(read(staff, candidatesUrl(projectId))).contains("Hidden Person");
            assertThat(read(staff, candidatesUrl(projectId) + "/" + hidden)).contains("Hidden Person");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/talent-map")).contains("Hidden Person");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/report")).contains("\"executivesMapped\":2");
            assertThat(read(staff, "/api/v1/projects/" + projectId + "/export/companies")).contains("Hidden Person");
        }
        String key = body(mvc.perform(post("/api/v1/workspace/api-keys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Universe","scopes":["companies:read","candidates:read"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("secret").asText();
        assertThat(read(key, "/api/v1/public/projects/" + projectId + "/universe")).contains("Hidden Person");
    }

    @Test
    @DisplayName("staff read who filed each executive beside the rows; a client seat reads no attribution at all")
    void whoFiledEachExecutiveIsStaffOnly() throws Exception {
        String company = db.queryForObject("select triage_company_id::text from app_lm_project_candidate "
                + "where id = ?::uuid", String.class, shown);
        String saras = body(write(researcher, "POST", candidatesUrl(projectId), """
                {"triageCompanyId":"%s","fullName":"Filed By Sara"}""".formatted(company))).get("id").asText();

        JsonNode staffRead = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(read(researcher, candidatesUrl(projectId)));
        assertThat(staffRead.at("/addedBy/" + shown + "/name").asText()).isEqualTo("Alok Kumar");
        assertThat(staffRead.at("/addedBy/" + saras + "/name").asText()).isEqualTo("sara");
        assertThat(staffRead.at("/addedBy/" + saras + "/userId").isNull()).isFalse();
        assertThat(staffRead.at("/candidates/0").has("addedBy")).isFalse();

        JsonNode clientRead = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(read(client, candidatesUrl(projectId)));
        assertThat(clientRead.get("addedBy").isNull()).isTrue();
        assertThat(read(client, candidatesUrl(projectId))).doesNotContain("Alok Kumar");
    }

    @Test
    @DisplayName("a hidden executive matches neither Companies filter and moves nobody's place in its status sort")
    void theCompaniesGridIgnoresAHiddenExecutiveForAClient() throws Exception {
        moveTo(projectId, shown, "interested");
        String masdar = capture(projectId, "Masdar");
        add(projectId, """
                {"triageCompanyId":"%s","fullName":"Other Person","status":"contacted"}""".formatted(masdar));
        hide(hidden);

        assertThat(companies(admin, "executiveQuery=hidden")).containsExactly("ACWA Power");
        assertThat(companies(client, "executiveQuery=hidden")).isEmpty();
        assertThat(companies(admin, "executiveStatuses=identified")).containsExactly("ACWA Power");
        assertThat(companies(client, "executiveStatuses=identified")).isEmpty();

        String byStatus = "sort=executiveStatus&direction=asc";
        assertThat(companies(researcher, byStatus)).as("staff rank ACWA by its hidden Identified executive")
                .containsExactly("ACWA Power", "Masdar");
        assertThat(companies(client, byStatus)).as("a client seat ranks it by the Interested one it can see")
                .containsExactly("Masdar", "ACWA Power");
        assertThat(companies(client, byStatus + "&executiveQuery=hidden")).isEmpty();
        assertThat(companies(client, byStatus + "&executiveStatuses=identified")).isEmpty();
        assertThat(companies(admin, byStatus + "&executiveStatuses=identified")).containsExactly("ACWA Power");
    }

    @ParameterizedTest(name = "hidden by {0}")
    @EnumSource(HiddenBy.class)
    @DisplayName("the projects list counts only the executives a client seat can see")
    void theProjectsListCountsWhatTheReaderSees(HiddenBy by) throws Exception {
        moveTo(projectId, hidden, "engaged");
        String masdar = capture(projectId, "Masdar");
        String alone = add(projectId, """
                {"triageCompanyId":"%s","fullName":"Lone Person"}""".formatted(masdar)).get("id").asText();
        hide(by, hidden);
        hide(by, alone);

        for (String staff : new String[] {admin, researcher}) {
            JsonNode whole = projectRow(staff);
            assertThat(whole.get("candidates").asLong()).isEqualTo(3);
            assertThat(whole.get("mappedCandidates").asLong()).isEqualTo(3);
            assertThat(whole.get("engagedCandidates").asLong()).isEqualTo(1);
            assertThat(whole.get("mappedCompanies").asLong()).isEqualTo(2);
        }
        JsonNode seen = projectRow(client);
        assertThat(seen.get("candidates").asLong()).isEqualTo(1);
        assertThat(seen.get("mappedCandidates").asLong()).isEqualTo(1);
        assertThat(seen.get("engagedCandidates").asLong()).isZero();
        assertThat(seen.get("mappedCompanies").asLong()).isEqualTo(1);
        assertThat(seen.get("stage").asText()).as("nobody a client sees was approached")
                .isNotEqualTo(projectRow(admin).get("stage").asText());
    }

    @ParameterizedTest(name = "hidden by {0}")
    @EnumSource(HiddenBy.class)
    @DisplayName("a colleague holding only the client seat reads as a client, through their session and their own API key")
    void aPersonalKeyReadsAsItsOwnersSeat(HiddenBy by) throws Exception {
        String colleague = member("omar");
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar","position":"Chair","email":"omar@%s"}""".formatted(domain)))
                .andExpect(status().isOk());
        hide(by, hidden);
        String key = body(mvc.perform(post("/api/v1/workspace/api-keys")
                        .header("Authorization", "Bearer " + colleague)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Omar's sheet","scopes":["projects:read","companies:read","candidates:read"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("secret").asText();
        String position = "/api/v1/public/projects/" + projectId;

        assertThat(read(colleague, candidatesUrl(projectId))).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(projectRow(colleague).get("candidates").asLong()).isEqualTo(1);
        assertThat(read(key, position + "/candidates")).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(mvc.perform(get(position + "/candidates/" + hidden).header("Authorization", "Bearer " + key))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(read(key, position + "/universe")).contains("Shown Person").doesNotContain("Hidden Person");
        assertThat(body(mvc.perform(get(position).header("Authorization", "Bearer " + key)).andReturn())
                .get("candidates").asLong()).isEqualTo(1);
        assertThat(body(mvc.perform(get(position + "/summary").header("Authorization", "Bearer " + key)).andReturn())
                .at("/executives/identified").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("asked again after a no, or the request lapsing, keeps them hidden until they say yes")
    void aNoStandsUntilAYes() throws Exception {
        answerShare(hidden, "DECLINED");
        db.update("update app_lm_person_consent set status = 'REQUESTED', answered_at = null, answered_via = null"
                + " where person_id = (select person_id from app_lm_project_candidate where id = ?::uuid)", hidden);
        assertThat(read(client, candidatesUrl(projectId))).doesNotContain("Hidden Person");
        db.update("update app_lm_person_consent set status = 'EXPIRED'"
                + " where person_id = (select person_id from app_lm_project_candidate where id = ?::uuid)", hidden);
        assertThat(read(client, candidatesUrl(projectId))).doesNotContain("Hidden Person");

        answerShare(hidden, "GIVEN");

        assertThat(read(client, candidatesUrl(projectId))).contains("Hidden Person");
    }

    @Test
    @DisplayName("asked and not yet answered, they stay visible: no answer only warns staff")
    void noAnswerOnlyWarns() throws Exception {
        db.update("""
                insert into app_lm_person_consent (workspace_id, person_id, purpose, project_id, status, requested_at)
                select workspace_id, person_id, 'SHARE_WITH_CLIENT', project_id, 'REQUESTED', now()
                from app_lm_project_candidate where id = ?::uuid""", hidden);

        assertThat(read(client, candidatesUrl(projectId))).contains("Hidden Person");
    }

    @Test
    @DisplayName("a lifted restriction brings them back for the client")
    void aLiftedRestrictionShowsThemAgain() throws Exception {
        hide(HiddenBy.RESTRICTION, hidden);
        assertThat(read(client, candidatesUrl(projectId))).doesNotContain("Hidden Person");

        write(admin, "PUT", "/api/v1/candidates/" + personOf(hidden) + "/processing-restriction", """
                {"restricted":false}""");

        assertThat(read(client, candidatesUrl(projectId))).contains("Hidden Person");
    }

    private void hide(HiddenBy by, String candidateId) throws Exception {
        switch (by) {
            case STAFF -> hide(candidateId);
            case RESTRICTION -> assertThat(write(admin, "PUT",
                    "/api/v1/candidates/" + personOf(candidateId) + "/processing-restriction", """
                            {"restricted":true,"reason":"Asked what we hold about them"}""")
                    .getResponse().getStatus()).isEqualTo(200);
            case SHARE_DECLINED -> answerShare(candidateId, "DECLINED");
            case SHARE_WITHDRAWN -> answerShare(candidateId, "WITHDRAWN");
        }
    }

    /** As the privacy page writes an answer: the share's status, then its event. */
    private void answerShare(String candidateId, String answer) {
        db.update("""
                insert into app_lm_person_consent (workspace_id, person_id, purpose, project_id, status, answered_at,
                    answered_via)
                select workspace_id, person_id, 'SHARE_WITH_CLIENT', project_id, ?, now(), 'PAGE'
                from app_lm_project_candidate where id = ?::uuid
                on conflict on constraint app_lm_person_consent_uk
                do update set status = excluded.status, answered_at = excluded.answered_at, answered_via = 'PAGE'""",
                answer, candidateId);
        db.update("""
                insert into app_lm_person_consent_event (workspace_id, person_id, consent_id, action, status, via,
                    occurred_at)
                select k.workspace_id, k.person_id, k.id, 'ANSWERED', ?, 'PAGE', clock_timestamp()
                from app_lm_person_consent k
                join app_lm_project_candidate c on c.person_id = k.person_id and c.project_id = k.project_id
                where c.id = ?::uuid and k.purpose = 'SHARE_WITH_CLIENT'""", answer, candidateId);
    }

    private String personOf(String candidateId) {
        return db.queryForObject("select person_id::text from app_lm_project_candidate where id = ?::uuid",
                String.class, candidateId);
    }

    private JsonNode pipelineRow(String candidateId) throws Exception {
        JsonNode pipeline = body(mvc.perform(get("/api/v1/projects/" + projectId + "/pipeline")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andReturn());
        for (JsonNode row : pipeline.findParents("candidateId")) {
            if (row.get("candidateId").asText().equals(candidateId)) {
                return row;
            }
        }
        throw new AssertionError("no pipeline row " + candidateId);
    }

    private void hide(String candidateId) throws Exception {
        assertThat(write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId + "/client-visibility", """
                {"clientVisible":false}""").getResponse().getStatus()).isEqualTo(200);
    }

    private List<String> companies(String token, String query) throws Exception {
        List<String> names = new ArrayList<>();
        body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).get("companies").forEach(company -> names.add(company.get("companyName").asText()));
        return names;
    }

    private JsonNode projectRow(String token) throws Exception {
        for (JsonNode row : body(mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn())) {
            if (row.get("id").asText().equals(projectId)) {
                return row;
            }
        }
        throw new AssertionError("no project " + projectId);
    }

    private String read(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
