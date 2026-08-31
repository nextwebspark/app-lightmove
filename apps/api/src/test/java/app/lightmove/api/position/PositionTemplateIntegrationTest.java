package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * The role-template library end to end: what the picker lists, what applying one writes into a brief,
 * what it deliberately leaves alone, and that every seeded template is a document the API can read.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class PositionTemplateIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("the picker lists the seeded library, and every template in it is readable")
    void libraryIsListedAndEveryTemplateApplies() throws Exception {
        String admin = adminOf("Catalog Firm");
        String clientId = createClient(admin, "Mubadala", "UAE");
        JsonNode library = templates(admin);

        assertThat(library.size()).isGreaterThanOrEqualTo(15);
        assertThat(codesOf(library)).contains("chief-executive-officer", "chief-financial-officer",
                "chief-compliance-officer", "head-of-compliance", "head-of-human-resources",
                "generic-executive");
        for (JsonNode template : library) {
            assertThat(template.get("shared").asBoolean()).isTrue();
            assertThat(template.get("title").asString()).isNotBlank();
            assertThat(template.get("discipline").asString()).isNotBlank();
        }

        // Applying every one of them proves each stored body deserialises and each panel is balanced:
        // a template whose weights do not total 100 would seed a brief the screen calls unready.
        String projectId = createProject(admin, clientId, "Head of Alchemy");
        for (JsonNode template : library) {
            JsonNode brief = applyTemplate(admin, projectId, template.get("id").asString());
            assertThat(sum(brief.get("assessment").get("technical")))
                    .describedAs(template.get("code").asString()).isEqualTo(100);
            assertThat(sum(brief.get("assessment").get("behavioural")))
                    .describedAs(template.get("code").asString()).isEqualTo(100);
            assertThat(brief.get("assessment").get("criteria").size()).isGreaterThan(0);
            assertThat(brief.get("details").get("responsibilities").size()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("applying a template redrafts the brief for that role")
    void applyingATemplateRedraftsTheBrief() throws Exception {
        String admin = adminOf("Redraft Firm");
        String projectId = createProject(admin, createClient(admin, "ADNOC", "UAE"), "Chief Financial Officer");

        // Seeded as a CFO from the title, then told it is really a compliance mandate.
        assertThat(readBrief(admin, projectId).get("details").get("department").asString())
                .isEqualTo("Finance");

        JsonNode brief = applyTemplate(admin, projectId, idOf(admin, "chief-compliance-officer"));
        assertThat(brief.get("details").get("department").asString()).isEqualTo("Compliance");
        assertThat(brief.get("details").get("seniority").asString()).isEqualTo("C_SUITE");
        assertThat(brief.get("details").get("narrative").asString()).contains("compliance framework");
        assertThat(brief.get("assessment").get("technical").get(0).get("name").asString())
                .isEqualTo("Regulatory Framework & Licensing");
        // The chart is rebuilt around the new role rather than merged with the old one.
        List<String> chart = titlesOf(brief.get("reporting").get("orgChart"));
        assertThat(chart).contains("Head of Financial Crime").doesNotContain("Head of Treasury");
        // The role title stays the mandate's — a template drafts the brief, it does not rename a search.
        assertThat(brief.get("details").get("roleTitle").asString()).isEqualTo("Chief Financial Officer");
        // As does the client's country, which no template has an opinion about.
        assertThat(brief.get("details").get("location").asString()).isEqualTo("UAE");
    }

    @Test
    @DisplayName("what a person typed survives the template that redraws the rest")
    void applyingATemplateKeepsWhatSomebodyTyped() throws Exception {
        String admin = adminOf("Preserve Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");

        putStep(admin, projectId, "context", """
                {"mandateReason":"SUCCESSION","businessDriver":"The incumbent retires in March.",
                 "strategicPriorities":[{"name":"Capital discipline","selected":true},
                                        {"name":"Lender confidence","selected":true}],
                 "confidential":true,"internalContext":"Not to be discussed with the incumbent"}""");
        putStep(admin, projectId, "compensation", """
                {"currency":"AED","salaryMin":90000,"salaryMax":120000,"baseSalaryMode":"MONTHLY",
                 "bonusValue":null,"bonusBasis":null,"incentiveType":null,"incentiveAmount":750000,
                 "incentiveVesting":null,"benefits":[]}""");
        putStep(admin, projectId, "criteria", """
                {"criteria":[{"text":"Arabic language skills","mode":"PREFERRED","fromBrief":false},
                             {"text":"Drafted by the old template","mode":"REQUIRED","fromBrief":true}]}""");
        putStep(admin, projectId, "reporting", """
                {"orgChart":[{"nodeId":"22222222-2222-4222-8222-222222222222","parentNodeId":null,
                              "title":null,"name":null,"mandateSeat":true,"canvasX":null,"canvasY":null}],
                 "teamSize":"38 across the finance function","noticeValue":90,"noticeUnit":"DAYS"}""");

        JsonNode brief = applyTemplate(admin, projectId, idOf(admin, "chief-operating-officer"));

        // Everything a template cannot know about this mandate is left exactly where it was.
        assertThat(brief.get("context").get("mandateReason").asString()).isEqualTo("SUCCESSION");
        assertThat(brief.get("context").get("businessDriver").asString())
                .isEqualTo("The incumbent retires in March.");
        assertThat(brief.get("context").get("internalContext").asString())
                .isEqualTo("Not to be discussed with the incumbent");
        assertThat(brief.get("context").get("confidential").asBoolean()).isTrue();
        assertThat(brief.get("compensation").get("salaryMin").asInt()).isEqualTo(90000);
        assertThat(brief.get("compensation").get("incentiveAmount").asInt()).isEqualTo(750000);
        assertThat(brief.get("reporting").get("teamSize").asString())
                .isEqualTo("38 across the finance function");

        // A criterion somebody wrote themselves survives; the one the old template drafted does not.
        List<String> criteria = new ArrayList<>();
        for (JsonNode criterion : brief.get("assessment").get("criteria")) {
            criteria.add(criterion.get("text").asString());
        }
        assertThat(criteria).contains("Arabic language skills")
                .doesNotContain("Drafted by the old template");

        // The palette becomes the new role's, keeping what was lit and the chip nobody offered.
        JsonNode priorities = brief.get("context").get("strategicPriorities");
        assertThat(priorities.get(0).get("name").asString()).isEqualTo("Operational excellence");
        assertThat(selectedNames(priorities)).containsExactlyInAnyOrder("Capital discipline",
                "Lender confidence");

        // The package's shape is the new template's, since that is the half a template speaks for.
        assertThat(brief.get("compensation").get("currency").asString()).isEqualTo("USD");
        assertThat(brief.get("compensation").get("bonusValue").asInt()).isEqualTo(40);
        assertThat(brief.get("compensation").get("benefits").size()).isEqualTo(5);
    }

    @Test
    @DisplayName("a template id nothing in this workspace answers to is a 404")
    void unknownTemplatesAreRefused() throws Exception {
        String admin = adminOf("Scope Firm");
        String projectId = createProject(admin, createClient(admin, "Emaar", "UAE"), "CFO");

        mvc.perform(post(positionUrl(projectId) + "/template")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        // The brief the refusal did not touch is still the one the title seeded.
        assertThat(readBrief(admin, projectId).get("details").get("department").asString())
                .isEqualTo("Finance");
    }

    @Test
    @DisplayName("a head-of title seeds the head-of template, not the C-suite one above it")
    void headOfTitlesSeedTheirOwnTemplate() throws Exception {
        String admin = adminOf("Matching Firm");
        String clientId = createClient(admin, "Emirates NBD", "UAE");

        JsonNode headOfCompliance = readBrief(admin, createProject(admin, clientId, "Head of Compliance"));
        assertThat(headOfCompliance.get("details").get("seniority").asString()).isEqualTo("N_MINUS_1");
        assertThat(titlesOf(headOfCompliance.get("reporting").get("orgChart")))
                .contains("Chief Compliance Officer");

        JsonNode chiefCompliance = readBrief(admin, createProject(admin, clientId, "Chief Compliance Officer"));
        assertThat(chiefCompliance.get("details").get("seniority").asString()).isEqualTo("C_SUITE");
        assertThat(titlesOf(chiefCompliance.get("reporting").get("orgChart")))
                .contains("Chief Executive Officer");
    }

    private JsonNode templates(String token) throws Exception {
        return body(mvc.perform(get("/api/v1/position-templates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String idOf(String token, String code) throws Exception {
        for (JsonNode template : templates(token)) {
            if (code.equals(template.get("code").asString())) {
                return template.get("id").asString();
            }
        }
        throw new AssertionError("No template coded " + code);
    }

    private JsonNode applyTemplate(String token, String projectId, String templateId) throws Exception {
        return body(mvc.perform(post(positionUrl(projectId) + "/template")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":\"%s\"}".formatted(templateId)))
                .andExpect(status().isOk())
                .andReturn());
    }

    private void putStep(String token, String projectId, String step, String payload) throws Exception {
        mvc.perform(put(positionUrl(projectId) + "/" + step)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private JsonNode readBrief(String token, String projectId) throws Exception {
        return body(mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static String positionUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/position";
    }

    private static List<String> codesOf(JsonNode templates) {
        List<String> codes = new ArrayList<>();
        for (JsonNode template : templates) {
            codes.add(template.get("code").asString());
        }
        return codes;
    }

    private static List<String> titlesOf(JsonNode chart) {
        List<String> titles = new ArrayList<>();
        for (JsonNode node : chart) {
            if (!node.get("title").isNull()) {
                titles.add(node.get("title").asString());
            }
        }
        return titles;
    }

    private static List<String> selectedNames(JsonNode priorities) {
        List<String> names = new ArrayList<>();
        for (JsonNode priority : priorities) {
            if (priority.get("selected").asBoolean()) {
                names.add(priority.get("name").asString());
            }
        }
        return names;
    }

    private static int sum(JsonNode panel) {
        int total = 0;
        for (JsonNode row : panel) {
            total += row.get("weight").asInt();
        }
        return total;
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String createClient(String token, String name, String hqCountry) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s","hqCountry":"%s"}
                                """.formatted(name, hqCountry)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String token, String clientId, String positionTitle) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s","targetDate":null}
                                """.formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
