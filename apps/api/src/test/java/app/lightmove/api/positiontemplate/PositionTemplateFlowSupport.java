package app.lightmove.api.positiontemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Plumbing for the template-management suites. The library is shared by every suite in the context and
 * nothing rolls back, so these suites never edit a seeded template: they create their own, keyed to a
 * word no other suite's role title contains, and each one stays valid enough for
 * {@code PositionTemplateIntegrationTest} to apply when it walks the whole library.
 */
public abstract class PositionTemplateFlowSupport extends FlowTestSupport {

    protected static final String LIBRARY = "/api/v1/platform/position-templates";
    protected static final String FIRM_TEMPLATES = "/api/v1/workspace/position-templates";
    protected static final String PICKER = "/api/v1/position-templates";

    @Autowired protected JdbcTemplate db;

    protected record Firm(String email, String token) {
    }

    protected Firm firm(String workspaceName, String localPart) throws Exception {
        String address = localPart + "@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), workspaceName);
        return new Firm(address, login(address));
    }

    /** What {@code ops/cloudsql/grant-platform-role.sh} does, without the script. */
    protected void grantSuperAdmin(String email) {
        db.update("""
                insert into app_lm_user_platform_role (user_id, role_id)
                select u.id, r.id from app_lm_user u, app_lm_role r
                where u.email = ? and r.scope = 'PLATFORM' and r.name = 'SUPER_ADMIN'
                """, email);
    }

    protected String superAdmin() throws Exception {
        Firm owner = firm("Platform Owner Firm", "owner");
        grantSuperAdmin(owner.email());
        return owner.token();
    }

    protected static String uniqueKeyword() {
        return "quill " + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String titleOf(String keyword) {
        return "Chief " + keyword;
    }

    /** One template as a file entry: both panels balanced, a criterion and a responsibility. */
    protected static String templateEntry(String title, String keyword, String department) {
        return """
                {"title":"%s","discipline":"FINANCE","seniority":"C_SUITE","summary":"Written by a test.",
                 "keywords":["%s"],
                 "body":{"department":"%s","responsibilities":["Run the function"],
                   "criteria":[{"text":"Has run the function at scale","mode":"REQUIRED"}],
                   "competencies":[
                     {"panel":"TECHNICAL","name":"Operating","description":"Runs it","weight":100},
                     {"panel":"BEHAVIOURAL","name":"Judgement","description":"Decides well","weight":100}]}}
                """.formatted(title, keyword, department);
    }

    protected static String templateRequest(String title, String keyword, String department, Long version) {
        String entry = templateEntry(title, keyword, department).strip();
        return entry.substring(0, entry.length() - 1) + ",\"version\":" + version + "}";
    }

    /** A template as its editor opened it, with the department changed — a file entry, or a save with its version. */
    protected String edited(JsonNode detail, String department, boolean withVersion) throws Exception {
        ObjectNode body = (ObjectNode) detail.get("body").deepCopy();
        body.put("department", department);
        Map<String, Object> request = new LinkedHashMap<>();
        for (String field : List.of("title", "discipline", "seniority", "summary", "keywords")) {
            request.put(field, detail.get(field));
        }
        request.put("body", body);
        if (withVersion) {
            request.put("version", detail.get("version").asLong());
        }
        return json.writeValueAsString(request);
    }

    protected static String fileOf(String... entries) {
        return "{\"format\":\"lightmove.position-templates\",\"formatVersion\":1,\"templates\":["
                + String.join(",", entries) + "]}";
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode getJson(String token, String url) throws Exception {
        return body(mvc.perform(get(url).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    protected MvcResult postJson(String token, String url, String payload) throws Exception {
        return mvc.perform(post(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
    }

    protected MvcResult putJson(String token, String url, String payload) throws Exception {
        return mvc.perform(put(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
    }

    protected MvcResult patchJson(String token, String url, String payload) throws Exception {
        return mvc.perform(patch(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn();
    }

    protected MvcResult uploadRaw(String token, String url, byte[] content) throws Exception {
        return mvc.perform(multipart(url)
                .file(new MockMultipartFile("file", "templates.json", "application/json", content))
                .header("Authorization", bearer(token))).andReturn();
    }

    protected JsonNode upload(String token, String url, byte[] content) throws Exception {
        return expect(200, uploadRaw(token, url, content));
    }

    protected byte[] download(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    protected JsonNode expect(int status, MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(status);
        return body(result);
    }

    protected void expectRefused(int status, String code, MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as(result.getResponse().getContentAsString())
                .isEqualTo(status);
        assertThat(codeOf(result)).isEqualTo(code);
    }

    protected String createClient(String token, String name) throws Exception {
        return expect(201, postJson(token, "/api/v1/clients", """
                {"customName":"%s","hqCountry":"UAE"}
                """.formatted(name))).get("id").asText();
    }

    protected String createProject(String token, String positionTitle) throws Exception {
        String clientId = createClient(token, "Client " + UUID.randomUUID().toString().substring(0, 6));
        return expect(201, postJson(token, "/api/v1/projects", """
                {"clientId":"%s","positionTitle":"%s","targetDate":null}
                """.formatted(clientId, positionTitle))).get("id").asText();
    }

    /** The brief a new mandate with this title is drafted as, in the caller's workspace. */
    protected JsonNode draftedBrief(String token, String positionTitle) throws Exception {
        return getJson(token, "/api/v1/projects/" + createProject(token, positionTitle) + "/position");
    }

    protected static List<String> codesIn(JsonNode templates) {
        List<String> codes = new ArrayList<>();
        for (JsonNode template : templates) {
            codes.add(template.get("code").asText());
        }
        return codes;
    }

    protected static JsonNode find(JsonNode templates, String code) {
        for (JsonNode template : templates) {
            if (code.equals(template.get("code").asText())) {
                return template;
            }
        }
        throw new AssertionError("No template coded " + code + " in " + templates);
    }
}
