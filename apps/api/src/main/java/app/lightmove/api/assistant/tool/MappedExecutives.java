package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * A page of the mandate's executives with the total, for {@code CompanyMatches}' reason: handed 25 rows
 * with no count, a model treats them as everyone. {@code companyNameTooBroad} says the name matched more
 * companies than one read takes, so even {@code matched} covers only some of them.
 */
public record MappedExecutives(long matched, int page, int showing, boolean companyNameTooBroad,
                               List<MappedExecutiveSummary> executives) {}
