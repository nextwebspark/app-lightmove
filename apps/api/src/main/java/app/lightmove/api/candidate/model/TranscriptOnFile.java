package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.List;

/** One recorded call with a person as the AI reads it: its title, when it started, and every line said. */
public record TranscriptOnFile(String title, Instant startsAt, List<SpokenLine> lines) {}
