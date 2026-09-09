/**
 * The HTTP contract for a mandate's triaged companies.
 *
 * <p>The two writes that create a row are separate endpoints because their trust models are opposites.
 * {@link app.lightmove.api.triagecompany.dto.AddTriageCompanyRequest} names a market company by
 * {@code apolloAccountId} and nothing else, so a caller cannot file one under a name of its own
 * choosing; {@link app.lightmove.api.triagecompany.dto.CaptureCompanyRequest} carries the fields
 * itself, because there is no universe row to resolve them from.
 */
package app.lightmove.api.triagecompany.dto;
