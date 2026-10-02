package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.OutreachStopReason;
import java.time.Instant;
import java.util.UUID;

/**
 * One person's run through a sequence: a row of People in outreach. {@code candidateStatus} is null once
 * the person is off the position; {@code endedAt} is when it was answered, bounced or stopped.
 */
public record OutreachRunResponse(UUID id, UUID candidateId, UUID personId, String fullName, String title,
                                  String companyName, String candidateStatus, UUID sequenceId, String sequenceName,
                                  int stepCount, int sentCount, Instant nextSendAt, Instant lastSentAt,
                                  EnrollmentStatus status, OutreachStopReason stopReason, Instant endedAt,
                                  UUID senderUserId, String senderName) {}
