package app.lightmove.api.pairing.service;

import app.lightmove.api.candidate.dto.AddedByResponse;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.CandidatesResponse;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.service.CandidatePipelineCatalog;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.config.ExportSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.security.rbac.ProjectAudience;
import app.lightmove.api.pairing.constant.StageLineSortField;
import app.lightmove.api.pairing.dto.StageLineResponse;
import app.lightmove.api.pairing.dto.StageLinesCriteria;
import app.lightmove.api.pairing.dto.StageLinesResponse;
import app.lightmove.api.pairing.model.PairedStage;
import app.lightmove.api.pairing.model.StageLineFacts;
import app.lightmove.api.pairing.model.StageLineSort;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.TriageCompanyFilters;
import app.lightmove.api.triagecompany.service.CompanyNoteService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The Companies grid's page: the whole stage paired into lines and ordered as one list by the sorted column, so a
 * person sorts by their own name or title whatever company they sit at. Reads as much of a stage as the export may.
 */
@Service
@RequiredArgsConstructor
public class StageLinesService {

    private final StagePairingService pairing;
    private final CandidateService candidates;
    private final CandidatePipelineCatalog pipeline;
    private final CompanyNoteService companyNotes;
    private final StageLineSortResolver sorts;
    private final LightMoveProperties properties;

    public StageLinesResponse page(UUID workspaceId, UUID projectId, StageLinesCriteria criteria,
                                   ProjectAudience audience) {
        int page = criteria.page() == null ? 0 : criteria.page();
        int size = criteria.size() == null ? properties.company().list().defaultPageSize() : criteria.size();
        properties.company().list().requireValidPage(page, size);
        TriageCompanyStatus status = TriageCompanyStatus.parseOrInUniverse(criteria.status());
        boolean staff = audience == ProjectAudience.STAFF;
        StageLineSort sort = sorts.resolve(workspaceId, projectId, criteria.sort(), criteria.direction(), staff);

        ExportSettings caps = properties.export();
        PairedStage paired = pairing.pair(workspaceId, projectId, status,
                new TriageCompanyFilters(criteria.query(), criteria.executiveQuery(),
                        criteria.executiveStatuses(), criteria.search()),
                caps.maxCompanies(), caps.maxCandidates(), audience, staff);

        List<StageLineResponse> lines = linesOf(paired);
        lines.sort(StageLineOrder.of(sort, factsFor(workspaceId, projectId, sort, paired, staff)));

        int from = Math.min(lines.size(), page * size);
        List<StageLineResponse> shown = lines.subList(from, Math.min(lines.size(), from + size));
        return new StageLinesResponse(withNoteCounts(projectId, shown, staff),
                staff ? addersOf(workspaceId, projectId, peopleOf(shown)) : null,
                lines.size(), page, size, paired.companies().counts(),
                paired.companies().totalCount() > caps.maxCompanies() || paired.totalCandidates() > caps.maxCandidates());
    }

    /** A company with nobody listed here keeps a line of its own, so the screen can offer to add someone there. */
    private static List<StageLineResponse> linesOf(PairedStage paired) {
        List<StageLineResponse> lines = new ArrayList<>();
        for (TriageCompanyResponse company : paired.companies().companies()) {
            List<CandidateResponse> people = paired.peopleAt(company.id());
            if (people.isEmpty()) {
                lines.add(new StageLineResponse(company, null));
                continue;
            }
            people.forEach(person -> lines.add(new StageLineResponse(company, person)));
        }
        paired.unassigned().forEach(person -> lines.add(new StageLineResponse(null, person)));
        return lines;
    }

    /** Only what this sort reads: every company's note count or every executive's adder is a read the page needs not. */
    private StageLineFacts factsFor(UUID workspaceId, UUID projectId, StageLineSort sort, PairedStage paired,
                                    boolean staff) {
        Map<String, Integer> statusRanks = sort.sortsBy(StageLineSortField.STATUS)
                ? statusRanksOf(workspaceId) : Map.of();
        Map<UUID, Integer> noteCounts = staff && sort.sortsBy(StageLineSortField.NOTES)
                ? companyNotes.countsOf(projectId,
                        paired.companies().companies().stream().map(TriageCompanyResponse::id).toList())
                : Map.of();
        Map<UUID, AddedByResponse> executiveAdders = staff && sort.sortsBy(StageLineSortField.ADDED_BY)
                ? addersOf(workspaceId, projectId, paired.people()) : Map.of();
        return new StageLineFacts(statusRanks, noteCounts, executiveAdders);
    }

    private Map<String, Integer> statusRanksOf(UUID workspaceId) {
        List<CandidateStatusDefinition> ordered = pipeline.catalogOf(workspaceId).statuses();
        Map<String, Integer> ranks = new HashMap<>();
        for (int rank = 0; rank < ordered.size(); rank++) {
            ranks.putIfAbsent(ordered.get(rank).getStatusKey(), rank);
        }
        return ranks;
    }

    private List<StageLineResponse> withNoteCounts(UUID projectId, List<StageLineResponse> shown, boolean staff) {
        if (!staff) {
            return List.copyOf(shown);
        }
        Set<UUID> companyIds = new LinkedHashSet<>();
        shown.stream().map(StageLineResponse::company).filter(Objects::nonNull)
                .forEach(company -> companyIds.add(company.id()));
        Map<UUID, Integer> counts = companyNotes.countsOf(projectId, companyIds);
        return shown.stream()
                .map(line -> line.company() == null ? line : new StageLineResponse(
                        line.company().withNoteCount(counts.getOrDefault(line.company().id(), 0)), line.candidate()))
                .toList();
    }

    private Map<UUID, AddedByResponse> addersOf(UUID workspaceId, UUID projectId, List<CandidateResponse> people) {
        if (people.isEmpty()) {
            return Map.of();
        }
        return candidates.withAddedBy(workspaceId, projectId,
                new CandidatesResponse(people, people.size(), 0, people.size())).addedBy();
    }

    private static List<CandidateResponse> peopleOf(List<StageLineResponse> shown) {
        return shown.stream().map(StageLineResponse::candidate).filter(Objects::nonNull).toList();
    }
}
