package app.lightmove.api.universe.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/** A company of the universe and how much of an anchor's niche it shares, rarest keyword first. */
@NamedInterface(name = "api", propagate = false)
public record ScoredCompanyRow(CompanyRow row, double score, List<String> sharedKeywords) {}
