package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.constant.StrategySectorKind;
import app.lightmove.api.project.dto.StrategyDtos.ChipDto;
import app.lightmove.api.project.dto.StrategyDtos.PutSectorsRequest;
import app.lightmove.api.project.dto.StrategyDtos.StrategyResponse;
import app.lightmove.api.project.model.Strategy;
import app.lightmove.api.project.model.StrategySector;
import app.lightmove.api.project.repository.ProjectRepository;
import app.lightmove.api.project.repository.StrategyRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The search strategy behind a project. Every load is scoped through the project's
 * {@code (id, workspaceId)} lookup — the workspace id comes from the principal, so a foreign project
 * 404s before any strategy row is touched. Seeded empty on first read (there is no template — an
 * empty scope is the honest start), and written as a whole-scope snapshot by the screen's autosave.
 */
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyRepository strategies;
    private final ProjectRepository projects;
    private final AuditService audit;

    @Transactional
    public StrategyResponse get(UUID workspaceId, UUID projectId) {
        return toResponse(load(projectId, workspaceId));
    }

    @Transactional
    public StrategyResponse putSectors(UUID userId, UUID workspaceId, UUID projectId,
                                       PutSectorsRequest request, HttpServletRequest httpRequest) {
        rejectDuplicatesWithin(request.direct(), StrategySectorKind.DIRECT);
        rejectDuplicatesWithin(request.adjacent(), StrategySectorKind.ADJACENT);
        rejectDuplicatesWithin(request.inferred(), StrategySectorKind.INFERRED);

        Strategy strategy = load(projectId, workspaceId);
        List<StrategySector> sectors = new ArrayList<>();
        appendKind(sectors, request.direct(), StrategySectorKind.DIRECT);
        appendKind(sectors, request.adjacent(), StrategySectorKind.ADJACENT);
        appendKind(sectors, request.inferred(), StrategySectorKind.INFERRED);
        strategy.replaceSectors(sectors);

        audit.event(ProjectEventType.STRATEGY_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("section", "sectors")
                .record();
        return toResponse(strategy);
    }

    private Strategy load(UUID projectId, UUID workspaceId) {
        requireProject(projectId, workspaceId);
        return strategies.findByProjectId(projectId)
                .orElseGet(() -> strategies.save(Strategy.forProject(projectId)));
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * Two chips with the same label in one group are a client bug, not a scope. The schema cannot
     * reject them (an element collection is rewritten in place mid-flush, so a unique index could
     * fire on a transient state), so the guard lives here — case-insensitively, since the labels are
     * verbatim taxonomy strings the user never retypes.
     */
    private static void rejectDuplicatesWithin(List<ChipDto> chips, StrategySectorKind kind) {
        Set<String> seen = new HashSet<>();
        for (ChipDto chip : chips) {
            if (!seen.add(chip.label().trim().toLowerCase(Locale.ROOT))) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Duplicate sector in the " + kind.name().toLowerCase(Locale.ROOT) + " group");
            }
        }
    }

    private static void appendKind(List<StrategySector> target, List<ChipDto> chips,
                                   StrategySectorKind kind) {
        for (ChipDto chip : chips) {
            target.add(StrategySector.of(kind, chip.label(), chip.selected()));
        }
    }

    private static StrategyResponse toResponse(Strategy strategy) {
        return new StrategyResponse(
                chipsOf(strategy, StrategySectorKind.DIRECT),
                chipsOf(strategy, StrategySectorKind.ADJACENT),
                chipsOf(strategy, StrategySectorKind.INFERRED));
    }

    private static List<ChipDto> chipsOf(Strategy strategy, StrategySectorKind kind) {
        return strategy.getSectors().stream()
                .filter(sector -> sector.getKind() == kind)
                .map(sector -> new ChipDto(sector.getLabel(), sector.isSelected()))
                .toList();
    }
}
