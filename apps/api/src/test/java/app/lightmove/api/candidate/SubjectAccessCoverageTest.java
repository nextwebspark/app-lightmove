package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.service.SubjectAccessContributor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * #919: every table holding a person is either exported by a {@link SubjectAccessContributor} or listed as excluded,
 * with its reason, in {@code docs/gdpr/subject-access-export.md} — so a table #922, #923 or #1267 adds cannot escape a
 * subject access request unnoticed — and the document says exactly what the contributors export.
 */
@IntegrationTest
class SubjectAccessCoverageTest {

    private static final Pattern ROW =
            Pattern.compile("^\\| `(app_lm_[a-z0-9_]+)` \\| (Included|Excluded) \\|", Pattern.MULTILINE);

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired List<SubjectAccessContributor> contributors;

    @Test
    @DisplayName("every person table is exported or excluded with a reason, and the document matches the code")
    void everyPersonTableIsDecided() throws IOException {
        Set<String> included = documented("Included");
        Set<String> excluded = documented("Excluded");
        Set<String> exported = contributors.stream().flatMap(contributor -> contributor.tables().stream())
                .collect(Collectors.toSet());
        List<String> personTables = db.queryForList("""
                WITH RECURSIVE person_table(table_name) AS (
                    SELECT 'app_lm_person'::information_schema.sql_identifier
                    UNION
                    SELECT c.table_name FROM information_schema.columns c
                    JOIN information_schema.tables t
                      ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                    WHERE c.table_schema = 'public' AND c.table_name LIKE 'app\\_lm\\_%'
                      AND t.table_type = 'BASE TABLE' AND c.column_name IN ('person_id', 'candidate_id')
                    UNION
                    SELECT child.table_name FROM information_schema.table_constraints child
                    JOIN information_schema.referential_constraints link
                      ON link.constraint_schema = child.constraint_schema
                     AND link.constraint_name = child.constraint_name
                    JOIN information_schema.table_constraints parent
                      ON parent.constraint_schema = link.unique_constraint_schema
                     AND parent.constraint_name = link.unique_constraint_name
                    JOIN person_table held ON held.table_name = parent.table_name
                    WHERE child.constraint_type = 'FOREIGN KEY' AND child.table_schema = 'public'
                )
                SELECT DISTINCT table_name FROM person_table
                """, String.class);
        Set<String> existing = new HashSet<>(db.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name LIKE 'app\\_lm\\_%'
                """, String.class));

        assertThat(personTables).as("the schema read found the tables it looks for")
                .contains("app_lm_person", "app_lm_task", "app_lm_outreach_message_attachment", "app_lm_note_mention");
        Set<String> decided = new HashSet<>(included);
        decided.addAll(excluded);
        assertThat(decided).as("person tables docs/gdpr/subject-access-export.md neither includes nor excludes")
                .containsAll(personTables);
        assertThat(included).as("the document's Included rows are exactly what the contributors export")
                .containsExactlyInAnyOrderElementsOf(exported);
        assertThat(excluded).as("tables both exported and excluded").doesNotContainAnyElementsOf(exported);
        assertThat(existing).as("rows of docs/gdpr/subject-access-export.md naming no table").containsAll(decided);
    }

    private static Set<String> documented(String decision) throws IOException {
        Matcher rows = ROW.matcher(Files.readString(repositoryRoot().resolve(Path.of("docs", "gdpr",
                "subject-access-export.md"))));
        Set<String> tables = new HashSet<>();
        while (rows.find()) {
            if (rows.group(2).equals(decision)) {
                tables.add(rows.group(1));
            }
        }
        return tables;
    }

    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(Path.of("apps", "api")))) {
                return dir;
            }
        }
        throw new IllegalStateException("No apps/api above " + Path.of("").toAbsolutePath());
    }
}
