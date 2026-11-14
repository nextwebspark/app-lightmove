package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.PersonTouch;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The approaches outreach holds for these people across every position: emails sent or waiting, replies, live
 * sequences. Declared here and answered by {@code outreach}, which already depends on {@code candidate}.
 */
public interface PersonApproachHistory {

    List<PersonTouch> touchesOf(UUID workspaceId, Collection<UUID> personIds);
}
