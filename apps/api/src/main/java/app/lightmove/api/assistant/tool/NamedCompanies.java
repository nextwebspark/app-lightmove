package app.lightmove.api.assistant.tool;

import java.util.List;

/** The answer to one name lookup. {@code note} says why nothing was looked up, when nothing was. */
public record NamedCompanies(List<NamedCompanyFinding> companies, String note) {}
