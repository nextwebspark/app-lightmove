package app.lightmove.api.pairing.service;

import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.pairing.model.PairedStage;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompaniesResponse;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pairs a stage's companies with their executives as the Companies grid does, for the talent map, the
 * export and the public API alike. An executive at no company rides the universe stage only, and not
 * under a company-name search. The executive filters are applied per person, because the stage read's
 * own are company-level. Not {@code @Transactional}: the two reads open their own.
 */
@Service
@RequiredArgsConstructor
public class StagePairingService {

    private final TriageCompanyReadService triage;
    private final CandidateService candidates;

    public PairedStage pair(UUID workspaceId, UUID projectId, TriageCompanyStatus status,
                            TriageCompanyFilters filters, int maxCompanies, int maxCandidates) {
        TriageCompaniesResponse stage = triage.listAllOfStage(workspaceId, projectId, status, filters, maxCompanies);
        CandidatesResponse everyone = candidates.listAllOfProject(workspaceId, projectId, maxCandidates);

        Set<UUID> stageCompanyIds = new HashSet<>();
        stage.companies().stream().map(TriageCompanyResponse::id).forEach(stageCompanyIds::add);
        boolean keepsUnassigned = status == TriageCompanyStatus.IN_UNIVERSE && blank(filters.companyName());

        Map<UUID, List<CandidateResponse>> byCompany = new LinkedHashMap<>();
        List<CandidateResponse> unassigned = new ArrayList<>();
        List<CandidateResponse> people = new ArrayList<>();
        for (CandidateResponse person : everyone.candidates()) {
            if (!matchesExecutiveFilters(person, filters)) {
                continue;
            }
            if (person.triageCompanyId() == null) {
                if (keepsUnassigned) {
                    unassigned.add(person);
                    people.add(person);
                }
            } else if (stageCompanyIds.contains(person.triageCompanyId())) {
                byCompany.computeIfAbsent(person.triageCompanyId(), key -> new ArrayList<>()).add(person);
                people.add(person);
            }
        }
        return new PairedStage(stage, byCompany, unassigned, people, everyone.totalCount());
    }

    private static boolean matchesExecutiveFilters(CandidateResponse person, TriageCompanyFilters filters) {
        if (!filters.executiveStatuses().isEmpty() && !filters.executiveStatuses().contains(person.status())) {
            return false;
        }
        String name = filters.executiveName();
        return blank(name)
                || person.fullName().toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
