package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.PersonTouch;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The approaches outreach holds for these people across every position: emails sent or waiting, replies, live
 * sequences. Declared here and answered by {@code outreach}, which already depends on {@code candidate}.
 */
public interface PersonApproachHistory {

    /** Emails and replies since {@code since}, and every live sequence however old. */
    List<PersonTouch> touchesSince(UUID workspaceId, Collection<UUID> personIds, Instant since);

    /** Before {@code since}: each person's newest email or reply per teammate and position. */
    List<PersonTouch> latestTouchesBefore(UUID workspaceId, Collection<UUID> personIds, Instant since);
}
