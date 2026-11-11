package app.lightmove.api.core.tenant;

import app.lightmove.api.core.config.LightMoveProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot where row-level security would be inert: a superuser/BYPASSRLS role, an unforced owned table, an
 * unprotected tenant table ({@link TenantTableGuard}) or a pooled connection left in Flyway's maintenance flag.
 * Runs before the web server and scheduler start (an {@code ApplicationRunner} would be too late).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RowLevelSecurityPosture implements SmartInitializingSingleton {

    private final JdbcTemplate jdbc;
    private final TenantTableGuard tenantTables;
    private final LightMoveProperties properties;

    @Override
    public void afterSingletonsInstantiated() {
        List<String> awaitingHarden = tenantTables.awaitingHarden();
        if (!awaitingHarden.isEmpty()) {
            log.warn("Row-level security waits on ops/cloudsql/harden.sql, run as postgres, for {}",
                    String.join(", ", awaitingHarden));
        }
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
        faults.addAll(tenantTables.faults());
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT app_lm_rls_maintenance()", Boolean.class))) {
            faults.add("app.rls_maintenance is on for a pooled connection");
        }
        return faults;
    }
}
