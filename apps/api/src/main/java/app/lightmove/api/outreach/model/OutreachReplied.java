package app.lightmove.api.outreach.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * A person wrote back into a thread Uncava started, published in the transaction that records the reply; which reply,
 * never what it says. {@code enrollmentId} is the run whose thread it was, null only for a thread no run wrote.
 */
@NamedInterface(name = "api", propagate = false)
public record OutreachReplied(UUID workspaceId, UUID projectId, UUID candidateId, UUID personId, UUID enrollmentId,
                              UUID senderUserId, UUID messageId) {}
