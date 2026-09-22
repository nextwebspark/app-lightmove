package app.lightmove.api.companydiscovery.model;

/**
 * One company the model proposed — <b>identifiers and a reason, never a figure</b>.
 *
 * <p>The three URLs are lookup keys rather than content. {@code websiteUrl} in particular is used to
 * find the company and is not rendered as the row's website unless a record supplied one, because a
 * homepage the model produced is a guess that happens to be checkable.
 *
 * <p>{@code fit} is the one number the model owns, and it is not an exception to the rule: it scores
 * relevance to the question that was asked, which is a judgement about the answer rather than a
 * claim about the company. It is drawn on the grid and never stored on a filed row.
 */
public record DiscoveredCandidate(String companyName, String linkedinUrl, String websiteUrl,
                                  String sourceUrl, String reason, Integer fit) {}
