package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * A page of the mandate's executives with the total, for {@code CompanyMatches}' reason: handed 25 rows
 * with no count, a model treats them as everyone.
 */
public record MappedExecutives(long matched, int page, int showing, List<MappedExecutiveSummary> executives) {}
