package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Who the dialog was opened on: one executive from the drawer, or the companies ticked on a stage — whose people
 * listed at {@code triageStatus}, that stage, when it is named (#903).
 */
public record EnrollmentCandidatesRequest(
        @Size(max = 200) List<UUID> candidateIds,
        @Size(max = 200) List<UUID> triageCompanyIds,
        @Size(max = 16) String triageStatus) {

    public List<UUID> candidateIdsOrEmpty() {
        return candidateIds == null ? List.of() : candidateIds;
    }

    public List<UUID> triageCompanyIdsOrEmpty() {
        return triageCompanyIds == null ? List.of() : triageCompanyIds;
    }
}
