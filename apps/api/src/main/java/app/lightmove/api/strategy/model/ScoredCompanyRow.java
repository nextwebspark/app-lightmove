package app.lightmove.api.strategy.model;

import java.util.List;

/** A company of the universe and how much of an anchor's niche it shares, rarest keyword first. */
public record ScoredCompanyRow(CompanyRow row, double score, List<String> sharedKeywords) {}
