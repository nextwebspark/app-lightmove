package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Who the dialog was opened on: one executive from the drawer, or the companies ticked on a stage. */
public record EnrollmentCandidatesRequest(
        @Size(max = 200) List<UUID> candidateIds,
        @Size(max = 200) List<UUID> triageCompanyIds) {

    public List<UUID> candidateIdsOrEmpty() {
        return candidateIds == null ? List.of() : candidateIds;
    }

    public List<UUID> triageCompanyIdsOrEmpty() {
        return triageCompanyIds == null ? List.of() : triageCompanyIds;
    }
}
