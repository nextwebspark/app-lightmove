package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MessageStatus;
import java.time.Instant;
import java.util.UUID;

/** The email as it now waits: due at {@code sendAt}, and in a thread already when it answers one. */
public record ScheduledMessageResponse(UUID id, MessageStatus status, Instant sendAt, String subject,
                                       boolean inThread) {}
