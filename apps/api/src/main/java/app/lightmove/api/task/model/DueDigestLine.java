package app.lightmove.api.task.model;

import java.time.Instant;
import java.util.UUID;

/** One task the digest may name: what, on whom, for which position, and when it is due. */
public record DueDigestLine(String title, UUID personId, String executiveName, UUID candidateId,
                            UUID projectId, String positionTitle, Instant dueAt) {}
