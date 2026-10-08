package app.lightmove.api.assistant.tool;

import java.util.List;

/** Companies found by what they do, closest first; {@code shortOf} is how many fewer than asked were found. */
public record CompaniesByActivity(List<String> searchedFor, List<DiscoveredCompany> companies,
                                  boolean searchedLinkedIn, int shortOf) {}
