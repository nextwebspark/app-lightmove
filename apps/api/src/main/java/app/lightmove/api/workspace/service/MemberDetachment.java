package app.lightmove.api.workspace.service;

import java.util.UUID;

/**
 * What removing a workspace member means for the features that reference the membership row.
 * Declared here and implemented by those features (currently {@code project}), so the dependency
 * stays one-way: project depends on workspace, never the reverse.
 */
public interface MemberDetachment {

    /** Throws {@code MEMBER_LEADS_PROJECTS} if removal would orphan work only this member can hand over. */
    void assertRemovable(UUID memberId);

    /** Releases every reference to the membership row (team seats, assignments). */
    void detach(UUID memberId);
}
