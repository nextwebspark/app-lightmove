package app.lightmove.api.core.security.rbac;

/**
 * Code-side names for the seeded PROJECT-scope roles in {@code app_lm_role}.
 *
 * <p>Orthogonal to the workspace tier — a workspace MEMBER may hold ADMIN on one project and
 * RESEARCHER on another. A seat may hold several roles at once: the creator starts as
 * {@code {ADMIN, LEAD}}. There is no hierarchy between them; a seat's permissions are the union of its
 * roles' actions, so combined powers are granted by combining roles.
 */
public enum ProjectRole {

    /** Owns the mandate: team and roles, client access, archival. Creator holds it from the start. */
    ADMIN,

    /** Runs the search day-to-day. A project may have several leads, or none (admins cover it). */
    LEAD,

    /** Executes: sourcing, triage, candidates, notes. */
    RESEARCHER,

    /** The hiring-company contact's seat. Groundwork only — seeded with no actions, never granted yet. */
    CLIENT
}
