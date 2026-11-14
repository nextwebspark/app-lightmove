package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.MessageDirection;
import java.time.Instant;
import java.util.UUID;

/** An email to or from a person, reduced to who, which position, which way and when. */
public interface PersonMessageTouch {

    UUID getPersonId();

    UUID getProjectId();

    UUID getSenderUserId();

    MessageDirection getDirection();

    Instant getAt();
}
