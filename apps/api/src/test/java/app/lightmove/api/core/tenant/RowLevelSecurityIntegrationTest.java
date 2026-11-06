package app.lightmove.api.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The database's own tenant fence (V184): with workspace A's setting a transaction reads and writes A's rows of
 * every protected table and nothing of B's, and with no setting it reads nothing at all — whatever the query, a
 * repository's or raw SQL's.
 */
@IntegrationTest
class RowLevelSecurityIntegrationTest extends FlowTestSupport {

    /** Each protected table's rows of one workspace, counted from above the fence. */
    private static final Map<String, String> ROWS_OF_A_WORKSPACE = new LinkedHashMap<>();

    static {
        for (String table : new String[] {"app_lm_project", "app_lm_project_candidate", "app_lm_project_triage_company",
                "app_lm_person", "app_lm_person_note", "app_lm_person_document", "app_lm_company_note"}) {
            ROWS_OF_A_WORKSPACE.put(table, "select count(*) from " + table + " where workspace_id = ?");
        }
        for (String table : new String[] {"app_lm_person_contact", "app_lm_person_tag",
                "app_lm_person_document_version", "app_lm_person_photo"}) {
            ROWS_OF_A_WORKSPACE.put(table, "select count(*) from " + table
                    + " child join app_lm_person p on p.id = child.person_id where p.workspace_id = ?");
        }
        // V190's two shapes, one table each kind the seed touches: a direct workspace, and a project's child.
        for (String table : new String[] {"app_lm_client", "app_lm_workspace_candidate_tag", "app_lm_person_activity"}) {
            ROWS_OF_A_WORKSPACE.put(table, "select count(*) from " + table + " where workspace_id = ?");
        }
        for (String table : new String[] {"app_lm_position", "app_lm_project_member"}) {
            ROWS_OF_A_WORKSPACE.put(table, "select count(*) from " + table
                    + " child join app_lm_project p on p.id = child.project_id where p.workspace_id = ?");
        }
    }

    @Autowired @FixtureJdbc JdbcTemplate fixture;
    @Autowired JdbcTemplate application;
    @Autowired TransactionTemplate transactions;
    @Autowired PersonRepository persons;
    @Autowired ProjectRepository projects;
    @Autowired RowLevelSecurityPosture posture;

    private Seeded firmA;
    private Seeded firmB;

    @BeforeEach
    void seedTwoFirms() throws Exception {
        firmA = seed("amal@" + domain, "Fence Firm A");
        firmB = seed("bilal@" + domain, "Fence Firm B");
    }

    @Test
    @DisplayName("every protected table is guarded: forced, and the runtime role is no superuser")
    void thePostureHolds() {
        assertThat(posture.faults()).isEmpty();
        assertThat(application.queryForObject("select current_user", String.class)).isEqualTo("lm_app");
    }

    @Test
    @DisplayName("with A's setting each table shows exactly A's rows; with none it shows nothing")
    void aWorkspaceReadsItsOwnRowsAndNothingElse() {
        ROWS_OF_A_WORKSPACE.forEach((table, rowsOfAWorkspace) -> {
            long ownRows = fixture.queryForObject(rowsOfAWorkspace, Long.class, firmA.workspaceId());
            assertThat(ownRows).as("A has rows in %s", table).isPositive();
            assertThat(visibleIn(firmA.workspaceId(), table)).as(table).isEqualTo(ownRows);
            assertThat(visibleIn(null, table)).as("%s with no workspace", table).isZero();
            assertThat(application.queryForObject("select count(*) from " + table, Long.class))
                    .as("%s outside a transaction", table).isZero();
        });
    }

    @Test
    @DisplayName("a repository read in A's scope finds A's person and project and not B's")
    void repositoriesSeeOnlyTheScopedWorkspace() {
        WorkspaceScope.run(firmA.workspaceId(), () -> {
            assertThat(persons.findById(firmA.personId())).isPresent();
            assertThat(persons.findById(firmB.personId())).isEmpty();
            assertThat(projects.findById(firmA.projectId())).isPresent();
            assertThat(projects.findById(firmB.projectId())).isEmpty();
            assertThat(projects.existsByIdAndArchivedAtIsNotNull(firmB.projectId())).isFalse();
        });
        assertThat(persons.findById(firmA.personId())).isEmpty();
    }

    @Test
    @DisplayName("in A's scope B's rows can be neither changed nor added to")
    void aWorkspaceCannotWriteAnother() {
        WorkspaceScope.run(firmA.workspaceId(), () -> transactions.executeWithoutResult(status -> assertThat(
                application.update("update app_lm_person set full_name = 'Taken' where id = ?", firmB.personId()))
                .isZero()));
        assertThat(fixture.queryForObject("select full_name from app_lm_person where id = ?", String.class,
                firmB.personId())).isNotEqualTo("Taken");

        // Each copies a row A may read, so the policy is the only thing that can refuse it.
        assertRefusedByPolicy(() -> application.update("""
                insert into app_lm_person (workspace_id, full_name, source, created_by)
                select ?, 'Planted', source, created_by from app_lm_person where id = ?""",
                firmB.workspaceId(), firmA.personId()));
        assertRefusedByPolicy(() -> application.update("""
                insert into app_lm_person_tag (person_id, tag_id)
                select ?, tag_id from app_lm_person_tag where person_id = ?""", firmB.personId(), firmA.personId()));
    }

    private void assertRefusedByPolicy(Runnable write) {
        assertThatThrownBy(() -> WorkspaceScope.run(firmA.workspaceId(), () -> transactions.executeWithoutResult(
                status -> write.run())))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("row-level security policy");
    }

    @Test
    @DisplayName("a pooled connection carries no workspace past the borrower that set it")
    void aBorrowedConnectionCarriesOnlyItsBorrowersWorkspace() {
        for (int borrow = 0; borrow < 20; borrow++) {
            WorkspaceScope.run(firmA.workspaceId(), () -> transactions.executeWithoutResult(status ->
                    assertThat(application.queryForObject("select app_lm_current_workspace()", UUID.class))
                            .isEqualTo(firmA.workspaceId())));
            assertThat(application.queryForObject("select app_lm_current_workspace()", UUID.class)).isNull();
        }
    }

    @Test
    @DisplayName("a scope is refused inside a transaction begun for another workspace")
    void aScopeCannotJoinAnotherWorkspacesTransaction() {
        WorkspaceScope.run(firmA.workspaceId(), () -> transactions.executeWithoutResult(status ->
                assertThatThrownBy(() -> WorkspaceScope.run(firmB.workspaceId(), () -> { }))
                        .isInstanceOf(IllegalStateException.class)));
    }

    /**
     * A webhook's or job's thread commits with no workspace, and its after-commit listener — run on that same thread
     * when the async pool is full, always in tests — joins the committed transaction's still-bound connection.
     */
    @Test
    @DisplayName("a scope entered after commit sets the still-bound connection to its workspace, then hands it back")
    void aScopeAfterCommitSetsAndRestoresTheBoundConnection() {
        Map<String, Object> seen = new HashMap<>();
        transactions.executeWithoutResult(status -> {
            seen.put("committedOn", backendPid());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    WorkspaceScope.run(firmA.workspaceId(), () -> {
                        seen.put("scopedOn", backendPid());
                        seen.put("scopedTo", currentWorkspace());
                        seen.put("ownPerson", personRows(firmA.personId()));
                        seen.put("otherPerson", personRows(firmB.personId()));
                    });
                    seen.put("afterScope", currentWorkspace());
                }
            });
        });

        assertThat(seen.get("scopedOn")).as("the scope joined the committed connection").isEqualTo(seen.get("committedOn"));
        assertThat(seen.get("scopedTo")).isEqualTo(firmA.workspaceId());
        assertThat(seen.get("ownPerson")).isEqualTo(1L);
        assertThat(seen.get("otherPerson")).isEqualTo(0L);
        assertThat(seen).containsKey("afterScope");
        assertThat(seen.get("afterScope")).as("the connection reads no workspace again").isNull();
    }

    @Test
    @DisplayName("the maintenance flag set on a borrowed connection is gone at the next checkout")
    void theMaintenanceFlagDiesWithItsBorrower() {
        for (int borrow = 0; borrow < 20; borrow++) {
            transactions.executeWithoutResult(status -> {
                application.queryForObject("select set_config('app.rls_maintenance', 'on', false)", String.class);
                assertThat(application.queryForObject("select count(*) from app_lm_person", Long.class)).isPositive();
            });
            assertThat(application.queryForObject("select app_lm_rls_maintenance()", Boolean.class)).isFalse();
            assertThat(application.queryForObject("select count(*) from app_lm_person", Long.class)).isZero();
        }
    }

    private Integer backendPid() {
        return application.queryForObject("select pg_backend_pid()", Integer.class);
    }

    private UUID currentWorkspace() {
        return application.queryForObject("select app_lm_current_workspace()", UUID.class);
    }

    private Long personRows(UUID personId) {
        return application.queryForObject("select count(*) from app_lm_person where id = ?", Long.class, personId);
    }

    private long visibleIn(UUID workspaceId, String table) {
        Long visible = workspaceId == null
                ? transactions.execute(status -> application.queryForObject("select count(*) from " + table, Long.class))
                : WorkspaceScope.call(workspaceId, () -> transactions.execute(status ->
                        application.queryForObject("select count(*) from " + table, Long.class)));
        return visible == null ? 0 : visible;
    }

    /** A firm with one row in every protected table, filed through the screens' own doors. */
    private Seeded seed(String adminEmail, String firmName) throws Exception {
        createWorkspace(verifiedUser("Fence Admin", adminEmail), firmName);
        String token = login(adminEmail);

        String clientId = body(mvc.perform(post("/api/v1/clients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"customName":"%s Unit"}""".formatted(firmName)))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer"}""".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        String companyId = body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s Holdings","note":"Strong finance bench."}""".formatted(firmName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        JsonNode candidate = body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Fenced Person","email":"fenced@%s.example","note":"Met in Dubai."}"""
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn());
        String candidateId = candidate.get("id").asText();
        UUID personId = UUID.fromString(candidate.get("personId").asText());

        mvc.perform(multipart("/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/documents")
                        .file(new MockMultipartFile("file", "cv.pdf", "application/pdf",
                                ("%PDF-1.7\n" + firmName).getBytes(StandardCharsets.US_ASCII)))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        String tagId = body(mvc.perform(get("/api/v1/candidate-tags").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).get(0).get("id").asText();
        mvc.perform(put("/api/v1/candidates/" + personId + "/tags/" + tagId).header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());

        fixture.update("insert into app_lm_person_photo (person_id, content, content_type) values (?, '\\x00', 'image/png')",
                personId);

        UUID workspaceId = fixture.queryForObject("select workspace_id from app_lm_project where id = ?::uuid",
                UUID.class, projectId);
        return new Seeded(workspaceId, UUID.fromString(projectId), personId, UUID.fromString(companyId));
    }

    private record Seeded(UUID workspaceId, UUID projectId, UUID personId, UUID companyId) {}
}
