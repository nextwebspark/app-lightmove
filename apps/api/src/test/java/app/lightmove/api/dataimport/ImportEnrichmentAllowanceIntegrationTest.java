package app.lightmove.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCompanyEnricher;
import app.lightmove.api.RecordingProfileEnricher;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import app.lightmove.api.enrichment.importrun.service.ImportEnrichmentWorker;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * The month's fair use holds per paid read, not only at the commit: a follow-on company read and a second import that
 * both passed the commit's check against the same headroom are refused once it is spent. One read at a time, so the
 * ceiling is exact here; in production it is soft by at most the parallelism, as every fair-use check is.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.billing.enforce=true",
        "lightmove.billing.fair-use.profile-enrich-per-seat=3",
        "lightmove.spreadsheet-import.enrichment-parallelism=1"})
class ImportEnrichmentAllowanceIntegrationTest extends FlowTestSupport {

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Chief Financial Officer", "Finance leader.", "ACWA Power",
            "https://www.linkedin.com/company/acwa-power/", null, "Riyadh", "Saudi Arabia",
            List.of(new CandidateCareerEntry("ACWA Power", "Chief Financial Officer", "2020 – Present", null)),
            List.of(), List.of(), List.of(), null, EnrichmentVendor.BRIGHTDATA);

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired private RecordingProfileEnricher profiles;
    @Autowired private RecordingCompanyEnricher companies;
    @Autowired private ImportEnrichmentWorker worker;

    @BeforeEach
    void freshVendors() {
        profiles.clear();
        companies.clear();
        profiles.answerWith(RESEARCH);
        companies.answerWith(new VendorCompanyRecord("acwa-power", "ACWA Power", "Utilities", "Saudi Arabia",
                "Riyadh", 3800, "https://acwa.example", "https://www.linkedin.com/company/acwa-power", 2004,
                "A power developer.", null, List.of("power"), "{}"));
        db.update("DELETE FROM app_lm_import_enrichment_item");
    }

    @Test
    @DisplayName("two imports that each passed the commit's check, and their follow-ons, never buy past the ceiling")
    void paidReadsStopAtTheCeiling() throws Exception {
        String admin = adminOf("Enrich Allowance Firm");
        String projectId = project(admin);
        UUID workspaceId = db.queryForObject("SELECT workspace_id FROM app_lm_project WHERE id = ?::uuid",
                UUID.class, projectId);
        db.update("""
                INSERT INTO app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats, status)
                VALUES (?, 'CORE', 'MONTHLY', 1, 'INVOICED')
                ON CONFLICT (workspace_id) DO UPDATE SET seats = 1""", workspaceId);

        importFile(admin, projectId, """
                Company,Full Name,LinkedIn
                ACWA Power,Layla Haddad,https://www.linkedin.com/in/layla-haddad/
                ACWA Power,Omar Nasser,https://www.linkedin.com/in/omar-nasser/
                """);
        importFile(admin, projectId, """
                Company,Full Name,LinkedIn
                Agthia Group,Sara Khan,https://www.linkedin.com/in/sara-khan/
                Agthia Group,Ali Saleh,https://www.linkedin.com/in/ali-saleh/
                """);
        for (int pass = 0; pass < 5; pass++) {
            worker.drain();
        }

        assertThat(profiles.fetchedUrls().size() + companies.fetchedSlugs().size()).isLessThanOrEqualTo(3);
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_import_enrichment_item WHERE workspace_id = ? AND status = 'OVER_ALLOWANCE'
                """, Integer.class, workspaceId)).isPositive();
        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_import_enrichment_item WHERE workspace_id = ? AND status IN ('QUEUED', 'RUNNING')
                """, Integer.class, workspaceId)).isZero();
    }

    private void importFile(String token, String projectId, String content) throws Exception {
        JsonNode preview = body(mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/preview")
                        .file(csv(content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        List<JsonNode> columns = new ArrayList<>();
        preview.get("columns").forEach(column -> columns.add(column.get("mapping")));
        String mapping = json.writeValueAsString(Map.of("columns", columns, "enrich", true));
        mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/commit")
                        .file(csv(content))
                        .file(new MockMultipartFile("mapping", "mapping.json",
                                MediaType.APPLICATION_JSON_VALUE, mapping.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "longlist.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String project(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Allowance Client %s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Group CFO"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
