package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.List;

/**
 * One recorded call with a person as the AI reads it: its title, when it started, every line said, and the call record
 * written from the whole of it ({@code null} for a call filed before records were kept).
 */
public record TranscriptOnFile(String title, Instant startsAt, List<SpokenLine> lines, String callRecord) {}
