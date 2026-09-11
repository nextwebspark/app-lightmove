package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.UUID;

/**
 * What the candidates list was asked for, gathered off the query string.
 *
 * <p>{@code triageCompanyIds} fetches the people at the companies on the page being rendered — the
 * grid is paged by company — and {@code unmapped} asks for the executives whose employer is not in
 * the universe at all. Both null means every candidate in the mandate.
 */
public record CandidateListCriteria(List<UUID> triageCompanyIds, Boolean unmapped, String nameQuery,
                                    Integer page, Integer size) {}
