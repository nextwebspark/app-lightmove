package app.uncava.api.task.model;

import java.time.Instant;

/** One task the digest names: what, on whom, for which position, and when it is due. */
public record DueDigestLine(String title, String executiveName, String positionTitle, Instant dueAt,
                            boolean overdue) {}
