package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MessageChannel;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageKind;
import java.time.Instant;

/** An executive's latest conversation line, as the queue previews it: the first words of its text. */
public record ConversationMessageSummaryResponse(MessageChannel channel, MessageDirection direction, MessageKind kind,
                                                 Instant at, String preview) {}
