package app.lightmove.api.dataimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import app.lightmove.api.enrichment.importrun.service.ImportEnrichmentPurge;
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
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.JsonNode;

/**
 * An import's LinkedIn enrichment, end to end against the recording vendors: what is queued, what the worker files,
 * that the file's own values always win, and that a file too big to enrich is refused before anything is written.
 */
@IntegrationTest
class ImportEnrichmentIntegrationTest extends FlowTestSupport {

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Chief Financial Officer", "Finance leader across GCC utilities.", "ACWA Power",
            "https://www.linkedin.com/company/acwa-power/", null, "Riyadh", "Saudi Arabia",
            List.of(new CandidateCareerEntry("ACWA Power", "Chief Financial Officer", "2020 – Present", null)),
            List.of(), List.of("Project Finance"), List.of("English", "Arabic"), null, EnrichmentVendor.BRIGHTDATA);

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired private RecordingProfileEnricher profiles;
    @Autowired private RecordingCompanyEnricher companies;
    @Autowired private ImportEnrichmentWorker worker;
    @Autowired private ImportEnrichmentPurge purge;

    @BeforeEach
    void freshVendors() {
        profiles.clear();
        companies.clear();
        db.update("DELETE FROM app_lm_import_enrichment_item");
    }

    @Test
    @DisplayName("an import that does not ask for enrichment queues nothing and buys nothing")
    void unaskedImportQueuesNothing() throws Exception {
        String admin = adminOf("Enrich Unasked Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Company,Full Name,LinkedIn
                ACWA Power,Layla Haddad,https://www.linkedin.com/in/layla-haddad/
                """, false, status().isOk());
        drainOnce(worker);

        assertThat(summary.get("enrichment").isNull()).isTrue();
        assertThat(profiles.fetchedUrls()).isEmpty();
        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_import_enrichment_item", Integer.class)).isZero();
    }

    @Test
    @DisplayName("an enriched import fills what the file left empty and never overwrites what it said")
    void enrichmentFillsOnlyTheBlanks() throws Exception {
        profiles.answerWith(RESEARCH);
        companies.answerWith(page("acwa-power", "ACWA Power", 3800));
        String admin = adminOf("Enrich Blanks Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Company,Company LinkedIn,Full Name,Job Title,LinkedIn
                ACWA Power,linkedin.com/company/acwa-power,Layla Haddad,CFO,linkedin.com/in/Layla-Haddad
                """, true, status().isOk());
        assertThat(summary.get("enrichment").get("peopleQueued").asInt()).isEqualTo(1);
        assertThat(summary.get("enrichment").get("companiesQueued").asInt()).isEqualTo(1);
        drainUntilDone(admin, projectId, summary.get("enrichment").get("runId").asText());

        assertThat(profiles.fetchedUrls()).containsExactly("https://www.linkedin.com/in/layla-haddad/");
        JsonNode person = candidates(admin, projectId).get("candidates").get(0);
        assertThat(person.get("title").asText()).isEqualTo("CFO");
        assertThat(person.get("summary").asText()).isEqualTo("Finance leader across GCC utilities.");
        JsonNode company = companies(admin, projectId).get("companies").get(0);
        assertThat(company.get("numEmployees").asInt()).isEqualTo(3800);
    }

    @Test
    @DisplayName("a company the file gave no page is researched from the page its person's research names")
    void researchesTheEmployerPageThePersonLeadsTo() throws Exception {
        profiles.answerWith(RESEARCH);
        companies.answerWith(page("acwa-power", "ACWA Power", 3800));
        String admin = adminOf("Enrich Employer Firm");
        String projectId = project(admin);

        JsonNode summary = importFile(admin, projectId, """
                Company,Full Name,LinkedIn
                ACWA Power,Layla Haddad,https://www.linkedin.com/in/layla-haddad/
                """, true, status().isOk());
        assertThat(summary.get("enrichment").get("companiesQueued").asInt()).isZero();
        drainUntilDone(admin, projectId, summary.get("enrichment").get("runId").asText());

        assertThat(companies.fetchedSlugs()).containsExactly("acwa-power");
        assertThat(companies(admin, projectId).get("companies").get(0).get("numEmployees").asInt())
                .isEqualTo(3800);
    }

    @Test
    @DisplayName("a file with more LinkedIn links than one import enriches is refused before anything is written")
    void refusesAFileTooBigToEnrich() throws Exception {
        String admin = adminOf("Enrich Too Big Firm");
        String projectId = project(admin);
        StringBuilder file = new StringBuilder("Company,Full Name,LinkedIn\n");
        for (int row = 0; row < 251; row++) {
            file.append("Firm ").append(row).append(",Person ").append(row)
                    .append(",https://www.linkedin.com/in/person-").append(row).append("/\n");
        }

        JsonNode problem = importFile(admin, projectId, file.toString(), true, status().isUnprocessableContent());

        assertThat(problem.get("code").asText()).isEqualTo("IMPORT_ENRICHMENT_TOO_LARGE");
        assertThat(problem.get("fieldErrors").get("enrich").asText()).contains("251").contains("250");
        assertThat(companies(admin, projectId).get("companies")).isEmpty();

        JsonNode unticked = importFile(admin, projectId, file.toString(), false, status().isOk());
        assertThat(unticked.get("candidatesCreated").asInt()).isEqualTo(251);
    }

    @Test
    @DisplayName("another workspace cannot read an import's enrichment progress")
    void progressIsTheWorkspacesOwn() throws Exception {
        profiles.answerWith(RESEARCH);
        String admin = adminOf("Enrich Progress Firm");
        String projectId = project(admin);
        JsonNode summary = importFile(admin, projectId, """
                Full Name,LinkedIn
                Layla Haddad,https://www.linkedin.com/in/layla-haddad/
                """, true, status().isOk());
        String runId = summary.get("enrichment").get("runId").asText();

        mvc.perform(get("/api/v1/projects/" + projectId + "/import/enrichment/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        JsonNode progress = body(mvc.perform(get("/api/v1/projects/" + projectId + "/import/enrichment/" + runId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(progress.get("total").asInt()).isEqualTo(1);
        assertThat(progress.get("done").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("erasing a person deletes their enrichment rows, by profile and by mandate row")
    void erasureDeletesThePersonsRows() throws Exception {
        String admin = adminOf("Enrich Erasure Firm");
        String projectId = project(admin);
        importFile(admin, projectId, """
                Full Name,LinkedIn
                Layla Haddad,https://www.linkedin.com/in/layla-haddad-erased/
                """, true, status().isOk());
        String personId = candidates(admin, projectId).get("candidates").get(0).get("personId").asText();
        assertThat(rowsFor("layla-haddad-erased")).isEqualTo(1);

        mvc.perform(post("/api/v1/candidates/" + personId + "/erase")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"confirmName":"Layla Haddad","suppress":false}"""))
                .andExpect(status().isNoContent());

        assertThat(rowsFor("layla-haddad-erased")).isZero();
    }

    @Test
    @DisplayName("a stale company record is bought again and metered; a fresh cached miss costs nothing")
    void companyReadsAreMeteredOnlyWhenTheVendorIsAsked() throws Exception {
        String admin = adminOf("Enrich Company Meter Firm");
        String projectId = project(admin);
        UUID workspaceId = db.queryForObject("SELECT workspace_id FROM app_lm_project WHERE id = ?::uuid",
                UUID.class, projectId);
        String staleSlug = "stale-co-" + UUID.randomUUID().toString().substring(0, 8);
        String missSlug = "missing-co-" + UUID.randomUUID().toString().substring(0, 8);
        String file = """
                Company,Company LinkedIn
                Stale Holdings,https://www.linkedin.com/company/%s
                """.formatted(staleSlug);

        companies.answerWith(page(staleSlug, "Stale Holdings", 120));
        drainUntilDone(admin, projectId, importFile(admin, projectId, file, true, status().isOk())
                .get("enrichment").get("runId").asText());
        db.update("UPDATE app_lm_vendor_company SET fetched_at = now() - interval '400 days' WHERE linkedin_slug = ?",
                staleSlug);
        drainUntilDone(admin, projectId, importFile(admin, projectId, file, true, status().isOk())
                .get("enrichment").get("runId").asText());

        assertThat(companies.fetchedSlugs()).containsExactly(staleSlug, staleSlug);
        assertThat(paidReads(workspaceId)).isEqualTo(2);

        companies.clear();
        String missFile = """
                Company,Company LinkedIn
                Missing Trading,https://www.linkedin.com/company/%s
                """.formatted(missSlug);
        drainUntilDone(admin, projectId, importFile(admin, projectId, missFile, true, status().isOk())
                .get("enrichment").get("runId").asText());
        drainUntilDone(admin, projectId, importFile(admin, projectId, missFile, true, status().isOk())
                .get("enrichment").get("runId").asText());

        assertThat(companies.fetchedSlugs()).containsExactly(missSlug);
        assertThat(paidReads(workspaceId)).isEqualTo(3);
    }

    private int rowsFor(String slug) {
        return db.queryForObject("SELECT count(*) FROM app_lm_import_enrichment_item WHERE linkedin_slug = ?",
                Integer.class, slug);
    }

    private long paidReads(UUID workspaceId) {
        return db.queryForObject("""
                SELECT coalesce(sum(units), 0) FROM app_lm_usage_event WHERE workspace_id = ? AND kind = 'PROFILE_ENRICH'
                """, Long.class, workspaceId);
    }

    @Test
    @DisplayName("a finished row past its keep-for is purged in a workspace that never imports again")
    void finishedRowsArePurgedInAnIdleWorkspace() throws Exception {
        String admin = adminOf("Enrich Purge Firm");
        String projectId = project(admin);
        UUID workspaceId = db.queryForObject("SELECT workspace_id FROM app_lm_project WHERE id = ?::uuid",
                UUID.class, projectId);
        insertFinished(workspaceId, projectId, "old-person", "now() - interval '31 days'");
        insertFinished(workspaceId, projectId, "recent-person", "now() - interval '1 day'");

        purge.purge();

        assertThat(db.queryForList("SELECT linkedin_slug FROM app_lm_import_enrichment_item WHERE workspace_id = ?",
                String.class, workspaceId)).containsExactly("recent-person");
    }

    private void insertFinished(UUID workspaceId, String projectId, String slug, String finishedAt) {
        db.update("""
                INSERT INTO app_lm_import_enrichment_item
                    (workspace_id, project_id, run_id, kind, target_id, linkedin_slug, status, finished_at)
                VALUES (?, ?::uuid, gen_random_uuid(), 'PERSON', gen_random_uuid(), ?, 'RESEARCHED', %s)
                """.formatted(finishedAt), workspaceId, projectId, slug);
    }

    private void drainUntilDone(String token, String projectId, String runId) throws Exception {
        for (int pass = 0; pass < 10; pass++) {
            drainOnce(worker);
            JsonNode progress = body(mvc.perform(get("/api/v1/projects/" + projectId + "/import/enrichment/" + runId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn());
            if (progress.get("done").asBoolean()) {
                return;
            }
        }
        throw new AssertionError("The enrichment run did not finish in ten passes");
    }

    /** The drain hands its reads to the pool and returns; a test waits for them before reading the outcome. */
    static void drainOnce(ImportEnrichmentWorker worker) throws InterruptedException {
        worker.drain();
        for (int wait = 0; wait < 500 && !worker.isIdle(); wait++) {
            Thread.sleep(10);
        }
    }

    private JsonNode importFile(String token, String projectId, String content, boolean enrich,
                                ResultMatcher expected) throws Exception {
        JsonNode preview = body(mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/preview")
                        .file(csv(content))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        List<JsonNode> columns = new ArrayList<>();
        preview.get("columns").forEach(column -> columns.add(column.get("mapping")));
        String mapping = json.writeValueAsString(Map.of("columns", columns, "enrich", enrich));

        return body(mvc.perform(multipart("/api/v1/projects/" + projectId + "/import/commit")
                        .file(csv(content))
                        .file(new MockMultipartFile("mapping", "mapping.json",
                                MediaType.APPLICATION_JSON_VALUE, mapping.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(expected)
                .andReturn());
    }

    private JsonNode companies(String token, String projectId) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode candidates(String token, String projectId) throws Exception {
        return body(mvc.perform(get("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static VendorCompanyRecord page(String slug, String name, int employees) {
        return new VendorCompanyRecord(slug, name, "Utilities", "Saudi Arabia", "Riyadh",
                employees, "https://" + slug + ".example", "https://www.linkedin.com/company/" + slug,
                2004, "A power developer.", null, List.of("power"), "{}");
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "longlist.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
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
                                {"customName":"Enrich Client %s"}""".formatted(UUID.randomUUID())))
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
