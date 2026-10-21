package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MessageBodyFormat;
import app.lightmove.api.outreach.constant.MessageChannel;
import app.lightmove.api.outreach.constant.MessageDirection;
import app.lightmove.api.outreach.constant.MessageKind;
import app.lightmove.api.outreach.constant.MessageStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import java.time.Instant;
import java.util.UUID;

/**
 * One conversation line in full. {@code text} is read as {@code format} says and drawn as text, never as HTML;
 * {@code stepNumber} and {@code sequenceName} are set on a sequence's email; {@code at} is a scheduled email's due
 * time, and {@code failReason} why a failed one never went. Staff-only.
 */
public record ConversationMessageResponse(
        UUID id,
        MessageDirection direction,
        MessageChannel channel,
        MessageKind kind,
        MessageStatus status,
        Instant at,
        String subject,
        String text,
        MessageBodyFormat format,
        String fromAddress,
        String toAddress,
        UUID senderUserId,
        String senderName,
        UUID enrollmentId,
        String sequenceName,
        Integer stepNumber,
        Integer stepCount,
        OutreachStopReason failReason
) {}
