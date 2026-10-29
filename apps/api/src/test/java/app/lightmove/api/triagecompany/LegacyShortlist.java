package app.lightmove.api.triagecompany;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Puts a company at Shortlisted the way an older release left it: no write files there any more, but a row already
 * there stays and every read still answers it. Carries the people standing where the company stood, as a move would.
 */
public final class LegacyShortlist {

    private LegacyShortlist() {}

    public static void shortlist(JdbcTemplate db, String triageCompanyId) {
        db.update("UPDATE app_lm_project_candidate c SET triage_status = 'SHORTLISTED' "
                + "FROM app_lm_project_triage_company t WHERE t.id = c.triage_company_id AND t.id = ?::uuid "
                + "AND c.triage_status = t.status", triageCompanyId);
        db.update("UPDATE app_lm_project_triage_company SET status = 'SHORTLISTED' WHERE id = ?::uuid",
                triageCompanyId);
    }
}
