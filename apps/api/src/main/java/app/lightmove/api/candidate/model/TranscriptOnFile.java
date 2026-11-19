package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.List;
import org.springframework.modulith.NamedInterface;

/** One recorded call with a person as the AI reads it: its title, when it started, and every line said. */
@NamedInterface(name = "api", propagate = false)
public record TranscriptOnFile(String title, Instant startsAt, List<SpokenLine> lines) {}
