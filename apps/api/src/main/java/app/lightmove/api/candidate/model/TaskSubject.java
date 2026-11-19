package app.lightmove.api.candidate.model;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** The executive a task is about: their row on the position, the person behind it, and the name on file. */
@NamedInterface(name = "api", propagate = false)
public record TaskSubject(UUID candidateId, UUID personId, String personName) {}
