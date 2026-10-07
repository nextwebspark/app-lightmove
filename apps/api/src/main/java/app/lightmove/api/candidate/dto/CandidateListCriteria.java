package app.lightmove.api.candidate.dto;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What the candidates list was asked for, gathered off the query string.
 *
 * <p>{@code triageCompanyIds} fetches the people at the companies on the page being rendered — the
 * grid is paged by company — and {@code unmapped} asks for the executives whose employer is not in
 * the universe at all. Both null means every candidate in the mandate. {@code status} is a
 * {@code CandidateStatus} wire token; null is every status. {@code textQuery} matches a name, a title or an
 * employer, where {@code nameQuery} matches the name alone, and {@code withinTriageCompanyIds} holds the people to a
 * whole stage's companies — unlike the page of companies {@code triageCompanyIds} names, never capped.
 */
public record CandidateListCriteria(List<UUID> triageCompanyIds, Boolean unmapped, String nameQuery,
                                    String textQuery, Collection<UUID> withinTriageCompanyIds,
                                    String status, Integer page, Integer size) {}
