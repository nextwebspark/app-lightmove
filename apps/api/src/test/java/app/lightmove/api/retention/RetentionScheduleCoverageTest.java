package app.lightmove.api.retention;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * #921: every table that holds a person, an account or an address is written down in
 * {@code docs/gdpr/retention-schedule.md} with how long it is kept — a new one fails here until it is — and the
 * schedule names no table that no longer exists.
 */
@IntegrationTest
class RetentionScheduleCoverageTest {

    private static final Pattern ROW = Pattern.compile("^\\| `(app_lm_[a-z0-9_]+)` \\|", Pattern.MULTILINE);

    @Autowired @FixtureJdbc JdbcTemplate db;

    @Test
    @DisplayName("every table holding personal data has a row in the retention schedule, and every row a table")
    void scheduleCoversEveryPersonalTable() throws IOException {
        Set<String> scheduled = scheduledTables();
        List<String> personal = db.queryForList("""
                SELECT DISTINCT c.table_name FROM information_schema.columns c
                JOIN information_schema.tables t ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE c.table_schema = 'public' AND c.table_name LIKE 'app\\_lm\\_%' AND t.table_type = 'BASE TABLE'
                  AND (c.column_name IN ('person_id', 'user_id', 'actor_user_id', 'recipient_user_id', 'full_name',
                                         'linkedin_url', 'to_address', 'from_address', 'email')
                       OR c.column_name LIKE '%\\_email' OR c.column_name LIKE 'email\\_%')
                """, String.class);
        Set<String> existing = new HashSet<>(db.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name LIKE 'app\\_lm\\_%'
                """, String.class));

        assertThat(personal).as("the schema read found the tables it looks for").contains("app_lm_person", "app_lm_user");
        assertThat(scheduled).as("tables holding personal data with no row in docs/gdpr/retention-schedule.md")
                .containsAll(personal);
        assertThat(existing).as("rows of docs/gdpr/retention-schedule.md naming no table").containsAll(scheduled);
    }

    private static Set<String> scheduledTables() throws IOException {
        Matcher rows = ROW.matcher(Files.readString(repositoryRoot().resolve(Path.of("docs", "gdpr",
                "retention-schedule.md"))));
        Set<String> tables = new HashSet<>();
        while (rows.find()) {
            tables.add(rows.group(1));
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
