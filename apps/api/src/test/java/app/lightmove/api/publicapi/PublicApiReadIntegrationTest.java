package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Reading positions, companies and executives with a key: the owner's seat or the whole workspace,
 * the scopes, the personal data held back without its own scope, and an audit line per read. The test
 * profile allows each key five requests a minute, so each test spends its keys with that in mind.
 */
@IntegrationTest
class PublicApiReadIntegrationTest extends FlowTestSupport {

    private static final String PROJECTS = "/api/v1/public/projects";

    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("a personal key reads the positions its owner is seated on; a workspace key reads every one, and no other workspace's")
    void personalKeyFollowsTheSeat() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String retail = project(admin, "Head of Retail");
        String sara = seatedResearcher(admin, finance);

        String saraKey = secret(sara, """
                {"name":"Sara's sheet","scopes":["projects:read"]}""");
        assertThat(titles(read(saraKey, PROJECTS, 200))).containsExactly("Chief Financial Officer");
        assertThat(read(saraKey, PROJECTS + "/" + finance, 200).get("title").asText())
                .isEqualTo("Chief Financial Officer");
        assertThat(read(saraKey, PROJECTS + "/" + retail, 403).get("code").asText()).isEqualTo("FORBIDDEN");

        String serviceKey = secret(admin, """
                {"name":"BI","kind":"SERVICE","scopes":["projects:read"]}""");
        assertThat(titles(read(serviceKey, PROJECTS, 200)))
                .containsExactlyInAnyOrder("Chief Financial Officer", "Head of Retail");
        assertThat(titles(read(serviceKey, PROJECTS + "?title=retail", 200))).containsExactly("Head of Retail");
        JsonNode firstPage = read(serviceKey, PROJECTS + "?size=1", 200);
        assertThat(firstPage.get("data")).hasSize(1);
        assertThat(firstPage.get("totalCount").asLong()).isEqualTo(2);
        assertThat(read(serviceKey, PROJECTS + "/" + retail, 200).get("clientName").asText()).startsWith("Client ");

        String outsider = secret(adminOf("other-" + domain), """
                {"name":"Elsewhere","kind":"SERVICE","scopes":["projects:read"]}""");
        assertThat(read(outsider, PROJECTS + "/" + finance, 404).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("a route outside the key's scopes is 403 API_KEY_SCOPE_MISSING, naming the scope it needs")
    void scopes() throws Exception {
        String admin = adminOf(domain);
        String projectId = project(admin, "Chief Financial Officer");

        String projectsOnly = secret(admin, """
                {"name":"Projects only","scopes":["projects:read"]}""");
        JsonNode companies = read(projectsOnly, PROJECTS + "/" + projectId + "/companies", 403);
        assertThat(companies.get("code").asText()).isEqualTo("API_KEY_SCOPE_MISSING");
        assertThat(companies.get("requiredScope").asText()).isEqualTo("companies:read");
        assertThat(read(projectsOnly, PROJECTS + "/" + projectId + "/candidates", 403).get("requiredScope").asText())
                .isEqualTo("candidates:read");

        String companiesOnly = secret(admin, """
                {"name":"Companies only","scopes":["companies:read"]}""");
        assertThat(read(companiesOnly, PROJECTS, 403).get("requiredScope").asText()).isEqualTo("projects:read");
        read(companiesOnly, PROJECTS + "/" + projectId + "/companies", 200);
    }

    @Test
    @DisplayName("contacts and compensation are null without their own scopes, a model's unconfirmed guess is null, and notes are never sent")
    void personalDataNeedsItsOwnScope() throws Exception {
        String admin = adminOf(domain);
        String projectId = project(admin, "Chief Financial Officer");
        String companyId = capture(admin, projectId, "ACWA Power");
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"Layla Haddad","title":"CFO",
                                 "emails":[{"value":"layla@acwa.example","kind":"work"}],
                                 "phones":[{"value":"+971 50 123 4567"}],
                                 "compensation":{"currency":"AED","baseSalary":1200000},
                                 "note":"Prefers a call after six"}""".formatted(companyId)))
                .andExpect(status().isCreated());
        db.update("""
                update app_lm_person set gender = 'FEMALE', nationality = 'Emirati', ai_inferred_fields = '["nationality"]'
                where workspace_id = (select workspace_id from app_lm_project where id = ?::uuid)""", projectId);
        String url = PROJECTS + "/" + projectId + "/candidates";

        MvcResult plainResult = mvc.perform(get(url).header("Authorization", "Bearer " + secret(admin, """
                        {"name":"Plain","scopes":["candidates:read"]}""")))
                .andExpect(status().isOk()).andReturn();
        JsonNode plain = body(plainResult).at("/data/0");
        assertThat(plain.get("fullName").asText()).isEqualTo("Layla Haddad");
        assertThat(plain.get("companyId").asText()).isEqualTo(companyId);
        assertThat(plain.get("contacts").isNull()).isTrue();
        assertThat(plain.get("compensation").isNull()).isTrue();
        assertThat(plain.get("gender").asText()).isEqualTo("female");
        assertThat(plain.get("nationality").isNull()).isTrue();
        assertThat(plain.has("customFields") || plain.has("aiInferredFields") || plain.has("note")).isFalse();
        assertThat(plainResult.getResponse().getContentAsString()).doesNotContain("Prefers a call");

        JsonNode full = read(secret(admin, """
                {"name":"Full","scopes":["candidates:read","candidates.contacts:read","candidates.compensation:read"]}"""),
                url, 200).at("/data/0");
        assertThat(full.at("/contacts/emails/0/address").asText()).isEqualTo("layla@acwa.example");
        assertThat(full.at("/contacts/emails/0/kind").asText()).isEqualTo("work");
        assertThat(full.at("/contacts/phones/0/number").asText()).isEqualTo("+971 50 123 4567");
        assertThat(full.at("/compensation/currency").asText()).isEqualTo("AED");
        assertThat(full.at("/compensation/baseSalary").asLong()).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("companies read a stage at a time, executives narrow by status and company, and a page is held to the ceiling")
    void filtersAndPages() throws Exception {
        String admin = adminOf(domain);
        String projectId = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, projectId, "ACWA Power");
        String masdar = capture(admin, projectId, "Masdar");
        mvc.perform(patch("/api/v1/projects/" + projectId + "/triage/" + masdar)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"shortlisted"}"""))
                .andExpect(status().isOk());
        map(admin, projectId, acwa, "Layla Haddad", "contacted");
        map(admin, projectId, acwa, "Omar Said", "identified");
        map(admin, projectId, masdar, "Yasmin Farouk", "identified");
        String scopes = """
                {"name":"Reader","scopes":["companies:read","candidates:read"]}""";
        String companies = PROJECTS + "/" + projectId + "/companies";
        String candidates = PROJECTS + "/" + projectId + "/candidates";

        String first = secret(admin, scopes);
        JsonNode shortlisted = read(first, companies + "?stage=shortlisted", 200);
        assertThat(shortlisted.get("totalCount").asLong()).isEqualTo(1);
        assertThat(shortlisted.at("/data/0/name").asText()).isEqualTo("Masdar");
        assertThat(shortlisted.at("/data/0/stage").asText()).isEqualTo("shortlisted");
        assertThat(read(first, companies + "?stage=archived", 400).get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(names(read(first, candidates + "?status=contacted", 200))).containsExactly("Layla Haddad");
        JsonNode atMasdar = read(first, candidates + "?companyId=" + masdar, 200);
        assertThat(names(atMasdar)).containsExactly("Yasmin Farouk");
        assertThat(atMasdar.get("size").asInt()).isEqualTo(25);

        String second = secret(admin, scopes);
        JsonNode paged = read(second, candidates + "?size=2&page=1", 200);
        assertThat(paged.get("totalCount").asLong()).isEqualTo(3);
        assertThat(paged.get("data")).hasSize(1);
        assertThat(paged.get("page").asInt()).isEqualTo(1);
        assertThat(read(second, candidates + "?size=101", 400).get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(read(second, candidates + "?status=hired", 400).get("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("every read is an audit line naming the key, the route and how many rows left")
    void auditsEveryRead() throws Exception {
        String admin = adminOf(domain);
        String projectId = project(admin, "Chief Financial Officer");
        capture(admin, projectId, "ACWA Power");
        JsonNode key = createKey(admin, """
                {"name":"BI","kind":"SERVICE","scopes":["projects:read","companies:read"]}""");
        String secret = key.get("secret").asText();
        String keyId = key.at("/key/id").asText();

        read(secret, PROJECTS, 200);
        read(secret, PROJECTS + "/" + projectId + "/companies", 200);

        Map<String, Object> companiesRead = db.queryForMap("""
                select target_id, actor_user_id, metadata->>'rows' as rows, metadata->>'kind' as kind
                from app_lm_audit_event
                where event_type = 'PUBLIC_API_READ' and metadata->>'keyId' = ?
                  and metadata->>'endpoint' = ?""", keyId, PROJECTS + "/" + projectId + "/companies");
        assertThat(companiesRead.get("target_id")).isEqualTo(projectId);
        assertThat(companiesRead.get("actor_user_id")).isNull();
        assertThat(companiesRead.get("rows")).isEqualTo("1");
        assertThat(companiesRead.get("kind")).isEqualTo("SERVICE");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'PUBLIC_API_READ' and metadata->>'keyId' = ?""", Long.class, keyId))
                .isEqualTo(2L);
    }

    private JsonNode read(String secret, String url, int expectedStatus) throws Exception {
        return body(mvc.perform(get(url).header("Authorization", "Bearer " + secret))
                .andExpect(status().is(expectedStatus)).andReturn());
    }

    private static List<String> titles(JsonNode list) {
        return list.get("data").valueStream().map(project -> project.get("title").asText()).toList();
    }

    private static List<String> names(JsonNode page) {
        return page.get("data").valueStream().map(candidate -> candidate.get("fullName").asText()).toList();
    }

    private String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Reads Firm");
        return login(alok);
    }

    private String seatedResearcher(String admin, String projectId) throws Exception {
        String sara = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        return login(sara);
    }

    private String project(String admin, String title) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Client %s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, title)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String capture(String admin, String projectId, String companyName) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s"}""".formatted(companyName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void map(String admin, String projectId, String companyId, String fullName, String status)
            throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"%s","status":"%s"}"""
                                .formatted(companyId, fullName, status)))
                .andExpect(status().isCreated());
    }

    private String secret(String bearer, String requestBody) throws Exception {
        return createKey(bearer, requestBody).get("secret").asText();
    }

    private JsonNode createKey(String bearer, String requestBody) throws Exception {
        return body(mvc.perform(post("/api/v1/workspace/api-keys").header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated()).andReturn());
    }
}
