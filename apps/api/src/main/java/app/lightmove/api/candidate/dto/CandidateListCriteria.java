package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/**
 * What the candidates list was asked for, gathered off the query string.
 *
 * <p>The two company filters answer the two halves of the Companies grid. {@code triageCompanyIds}
 * fetches the people at the companies on the page being rendered — the grid is paged by company, so
 * asking for the whole mandate's candidates would grow without bound as a mapping fills in.
 * {@code unmapped} asks for the rest: the executives whose employer is not in the universe at all.
 *
 * <p>Both null means every candidate in the mandate, which is what the profile drawer and a future
 * Candidates screen want.
 */
public record CandidateListCriteria(List<UUID> triageCompanyIds, Boolean unmapped, String nameQuery,
                                    Integer page, Integer size) {}
