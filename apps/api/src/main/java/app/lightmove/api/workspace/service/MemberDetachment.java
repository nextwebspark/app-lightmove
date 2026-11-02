package app.lightmove.api.workspace.service;

import app.lightmove.api.workspace.model.LedAlonePosition;
import java.util.List;
import java.util.UUID;

/** Member removal as the referencing features see it; implemented in {@code project}, keeping the dependency one-way. */
public interface MemberDetachment {

    /** Throws {@code MEMBER_LEADS_PROJECTS} if removal would orphan work only this member can hand over. */
    void assertRemovable(UUID memberId);

    /** The positions {@link #assertRemovable} refuses over, named for a person deleting their account. */
    List<LedAlonePosition> positionsLedAloneBy(UUID memberId);

    /** Releases every reference to the membership row (team seats, assignments). */
    void detach(UUID memberId);
}
