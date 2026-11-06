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
 * Refuses to boot where row-level security would be inert — a runtime role that is a superuser or holds
 * BYPASSRLS skips every policy, an owner skips a table that is not forced, a tenant table nobody protected
 * ({@link TenantTableGuard}) has no fence at all, and Flyway's maintenance flag left on a
 * pooled connection lets that connection read every workspace. A deploy that fails here keeps the old revision
 * serving, which beats a new one quietly unprotected.
 *
 * <p>Checked once every singleton exists — Flyway's migration included — and before the context's refresh starts the
 * web server and the scheduler, so an unprotected revision never serves a request or runs a job. An
 * {@code ApplicationRunner} would run only after both had started.
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
