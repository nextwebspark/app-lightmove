package app.lightmove.api.project.model;

/**
 * The archived positions, in JPQL and in SQL: the one definition every read that leaves them out — My tasks,
 * the due scan, the bell — is written against, as {@code project_id NOT IN (…)}.
 */
public final class ArchivedProjects {

    public static final String IDS_JPQL = "select archived.id from Project archived where archived.archivedAt is not null";
    public static final String IDS_SQL =
            "SELECT archived.id FROM app_lm_project archived WHERE archived.archived_at IS NOT NULL";

    private ArchivedProjects() {
    }
}
