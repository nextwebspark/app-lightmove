package app.lightmove.api.assistant.tool;

import java.util.List;

/** The mandate's universe companies with nobody mapped at them, and how many there are in all. */
public record UnmappedCompanies(long unmapped, long inUniverse, List<String> companyNames) {}
