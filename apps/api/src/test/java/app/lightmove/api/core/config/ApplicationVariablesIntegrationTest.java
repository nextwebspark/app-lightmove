package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.reload.PlatformConfigReload;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** #1314: what an application variable answers, and how a change made on one instance reaches another. */
@IntegrationTest
// So a reload seen without recheck() can only have come through the notification.
@TestPropertySource(properties = "lightmove.platform.config-reload-check=PT1H")
class ApplicationVariablesIntegrationTest {

    private static final ApplicationVariable COMPANIES = ApplicationVariable.SOURCING_MAX_COMPANIES_PER_RUN;

    @Autowired ApplicationVariables variables;
    @Autowired ApplicationVariableStore store;
    @Autowired PlatformConfigReload reload;
    @Autowired TransactionTemplate transactions;
    @Autowired @FixtureJdbc JdbcTemplate db;

    @AfterEach
    void serveTheConfiguredValuesAgain() {
        db.execute("ALTER TABLE IF EXISTS app_lm_platform_setting_away RENAME TO app_lm_platform_setting");
        db.update("DELETE FROM app_lm_platform_setting");
        variables.reload();
    }

    @Test
    @DisplayName("with no override a variable is its configured value")
    void noOverrideIsTheConfiguredValue() {
        assertThat(variables.intValue(COMPANIES)).isEqualTo(5);
        assertThat(variables.intValue(ApplicationVariable.SOURCING_RUN_DEADLINE)).isEqualTo(180);
    }

    @Test
    @DisplayName("a change serves on an instance the notification reached, with no timed check")
    void aChangeServesOnceTheNotificationArrives() {
        variables.intValue(COMPANIES);

        transactions.executeWithoutResult(status -> {
            store.override(COMPANIES.getKey(), "8", null);
            reload.bump(ApplicationVariables.SCOPE);
        });

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(variables.intValue(COMPANIES)).isEqualTo(8));
    }

    @Test
    @DisplayName("a change serves on an instance that missed the notification and only re-read the version numbers")
    void aChangeServesAfterTheBackstopCheck() {
        variables.intValue(COMPANIES);
        // Written past the application, so nothing is notified: only the version number moves.
        db.update("INSERT INTO app_lm_platform_setting (key, value) VALUES (?, '8')", COMPANIES.getKey());
        db.update("""
                INSERT INTO app_lm_platform_config_version (scope, version) VALUES ('settings', 1)
                ON CONFLICT (scope) DO UPDATE SET version = app_lm_platform_config_version.version + 1
                """);
        assertThat(variables.intValue(COMPANIES)).isEqualTo(5);

        reload.recheck();

        assertThat(variables.intValue(COMPANIES)).isEqualTo(8);
    }

    @Test
    @DisplayName("a stored value out of bounds, not a number, or for a key off the list is ignored")
    void anUnusableStoredValueServesTheConfiguredOne() {
        db.update("INSERT INTO app_lm_platform_setting (key, value) VALUES (?, '900')", COMPANIES.getKey());
        db.update("INSERT INTO app_lm_platform_setting (key, value) VALUES (?, 'many')",
                ApplicationVariable.SOURCING_PARALLELISM.getKey());
        db.update("INSERT INTO app_lm_platform_setting (key, value) VALUES ('auth.lockout.max-attempts', '100')");
        variables.reload();

        assertThat(variables.intValue(COMPANIES)).isEqualTo(5);
        assertThat(variables.intValue(ApplicationVariable.SOURCING_PARALLELISM)).isEqualTo(4);
        assertThat(variables.storedOverrides()).isEmpty();
    }

    @Test
    @DisplayName("with the table unreadable every variable is its configured value, and the caller is not failed")
    void anUnreadableTableServesTheConfiguredValues() {
        db.update("INSERT INTO app_lm_platform_setting (key, value) VALUES (?, '8')", COMPANIES.getKey());
        db.execute("ALTER TABLE app_lm_platform_setting RENAME TO app_lm_platform_setting_away");
        variables.reload();

        assertThat(variables.intValue(COMPANIES)).isEqualTo(5);
    }
}
