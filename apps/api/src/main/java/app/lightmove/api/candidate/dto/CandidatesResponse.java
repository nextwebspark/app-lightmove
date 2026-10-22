package app.lightmove.api.candidate.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One page of a mandate's mapped executives. {@code addedBy}, keyed by candidate id, is the Companies grid's staff
 * read alone, beside the rows rather than on them: {@code CandidateResponse} is read by a client seat too.
 */
public record CandidatesResponse(List<CandidateResponse> candidates, long totalCount, int page,
                                 int size, Map<UUID, AddedByResponse> addedBy) {

    public CandidatesResponse(List<CandidateResponse> candidates, long totalCount, int page, int size) {
        this(candidates, totalCount, page, size, null);
    }

    public CandidatesResponse withAddedBy(Map<UUID, AddedByResponse> attribution) {
        return new CandidatesResponse(candidates, totalCount, page, size, attribution);
    }
}
