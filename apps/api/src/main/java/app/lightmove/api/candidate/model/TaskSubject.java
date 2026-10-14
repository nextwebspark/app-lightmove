package app.lightmove.api.candidate.model;

import java.util.UUID;

/** The executive a task is about: their row on the position, the person behind it, and the name on file. */
public record TaskSubject(UUID candidateId, UUID personId, String personName) {}
