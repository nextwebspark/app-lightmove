package app.lightmove.api.candidate.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/**
 * A person stopped outreach emails, in the transaction that marked them. {@code recipientUserId} is who hears
 * of it — their owner, else whoever sent the email they used — and null when neither is known.
 */
@NamedInterface(name = "api", propagate = false)
public record PersonOptedOut(UUID workspaceId, UUID personId, UUID recipientUserId) {}
