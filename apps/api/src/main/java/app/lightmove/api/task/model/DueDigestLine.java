package app.lightmove.api.task.model;

import java.time.Instant;
import org.springframework.modulith.NamedInterface;

/** One task the digest names: what, on whom, for which position, and when it is due. */
@NamedInterface(name = "api", propagate = false)
public record DueDigestLine(String title, String executiveName, String positionTitle, Instant dueAt,
                            boolean overdue) {}
