package app.lightmove.api.billing.credit.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lets a billing job run once across instances: the instance whose claim row lands, committed before it acts. A run
 * that fails gives its claim back so the next one retries; claims older than {@link #KEPT_DAYS} days are pruned.
 */
@Component
@RequiredArgsConstructor
public class BillingJobRuns {

    static final int KEPT_DAYS = 90;

    private final JdbcTemplate jdbc;

    public boolean claim(String job, String runKey) {
        jdbc.update("""
                DELETE FROM app_lm_billing_job_run
                WHERE job = ? AND claimed_at < now() - make_interval(days => ?)""", job, KEPT_DAYS);
        return jdbc.update("""
                INSERT INTO app_lm_billing_job_run (job, run_key) VALUES (?, ?)
                ON CONFLICT DO NOTHING""", job, runKey) == 1;
    }

    public void giveBack(String job, String runKey) {
        jdbc.update("DELETE FROM app_lm_billing_job_run WHERE job = ? AND run_key = ?", job, runKey);
    }
}
