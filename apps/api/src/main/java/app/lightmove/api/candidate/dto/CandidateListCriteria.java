package app.lightmove.api.candidate.dto;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The candidates list's query: {@code triageCompanyIds} is the rendered page's companies and {@code unmapped} the
 * executives outside the universe (both null: everyone); {@code withinTriageCompanyIds} holds people to a whole stage,
 * never capped; {@code triageStatus} is the person's own stage (V150); {@code search} matches a person or their company.
 */
public record CandidateListCriteria(List<UUID> triageCompanyIds, Boolean unmapped, String nameQuery,
                                    String textQuery, Collection<UUID> withinTriageCompanyIds,
                                    String status, String pipelineStage, String triageStatus,
                                    String search, Integer page, Integer size) {}
