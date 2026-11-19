package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** The recorded call a change came from, as its timeline line names it. */
@NamedInterface(name = "api", propagate = false)
public record CallReference(UUID recordingId, String title, Instant startsAt) {}
