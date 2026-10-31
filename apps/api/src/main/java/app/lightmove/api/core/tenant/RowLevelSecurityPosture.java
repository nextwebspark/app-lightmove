package app.lightmove.api.core.tenant;

import app.lightmove.api.core.config.LightMoveProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot where row-level security would be inert — a runtime role that is a superuser or holds
 * BYPASSRLS skips every policy, an owner skips a table that is not forced, and Flyway's maintenance flag left on a
 * pooled connection lets that connection read every workspace. A deploy that fails here keeps the old revision
 * serving, which beats a new one quietly unprotected.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RowLevelSecurityPosture implements ApplicationRunner {

    /** Every table V166 protects; #1085 extends the list. */
    public static final List<String> PROTECTED_TABLES = List.of(
            "app_lm_project", "app_lm_project_candidate", "app_lm_project_triage_company",
            "app_lm_person", "app_lm_person_contact", "app_lm_person_note", "app_lm_person_document",
            "app_lm_person_document_version", "app_lm_person_tag", "app_lm_person_photo", "app_lm_company_note");

    private final JdbcTemplate jdbc;
    private final LightMoveProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        List<String> faults = faults();
        if (faults.isEmpty()) {
            return;
        }
        String report = "Row-level security is not in force: " + String.join("; ", faults);
        if (properties.rowLevelSecurity().requireEnforced()) {
            throw new IllegalStateException(report);
        }
        log.warn(report);
    }

    public List<String> faults() {
        List<String> faults = new ArrayList<>();
        Boolean exempt = jdbc.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);
        if (Boolean.TRUE.equals(exempt)) {
            faults.add("the runtime role is a superuser or holds BYPASSRLS");
        }
        List<String> unforced = jdbc.queryForList("""
                SELECT t.name FROM unnest(?::text[]) AS t(name)
                LEFT JOIN pg_class c ON c.relname = t.name AND c.relnamespace = 'public'::regnamespace
                WHERE c.oid IS NULL OR NOT (c.relrowsecurity AND c.relforcerowsecurity)
                ORDER BY t.name""", String.class, (Object) PROTECTED_TABLES.toArray(String[]::new));
        if (!unforced.isEmpty()) {
            faults.add("not forced on " + String.join(", ", unforced));
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT app_lm_rls_maintenance()", Boolean.class))) {
            faults.add("app.rls_maintenance is on for a pooled connection");
        }
        return faults;
    }
}
