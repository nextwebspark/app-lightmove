package app.lightmove.api.position.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionDetails;
import app.lightmove.api.position.model.MandateContext;
import app.lightmove.api.position.model.PositionOrgNode;
import app.lightmove.api.position.model.PositionSeed;
import app.lightmove.api.position.model.ReportingStructure;
import app.lightmove.api.position.repository.PositionRepository;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the brief behind a mandate, drafting one when there is none.
 *
 * <p>Every load goes through the project's {@code (id, workspaceId)} lookup, and the workspace id
 * comes from the principal — so a foreign mandate 404s before any brief row is touched.
 *
 * <p>Drafting happens on two paths and must behave identically on both: when a mandate is created,
 * and lazily on first read for the mandates that predate the position tables.
 */
@Component
@RequiredArgsConstructor
class PositionBriefLoader {

    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final ClientRepository clients;

    PositionBrief require(UUID workspaceId, UUID projectId) {
        Project project = projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        Position position = positions.findByProjectId(project.getId())
                .orElseGet(() -> draft(project.getId(), project.getPositionTitle(),
                        hqCountryOf(project.getClientId(), workspaceId)));
        return new PositionBrief(project, position);
    }

    /**
     * The seeded brief a new mandate starts from: the template matched on its role title, with the
     * client's home country pre-filled as the location.
     */
    Position draft(UUID projectId, String positionTitle, String location) {
        PositionSeed seed = PositionTemplates.forTitle(positionTitle);

        Position position = Position.forProject(projectId);
        position.applyDetails(new PositionDetails(
                null, location, EmploymentType.FULL_TIME_PERMANENT, seed.seniority(),
                seed.responsibilities(), seed.narrative()));
        position.applyContext(new MandateContext(
                MandateReason.NEW_ROLE, null, PositionTemplates.startingPriorities(), false, null));
        position.applyReporting(new ReportingStructure(seededChart(seed.reportsTo()), null, null, null));
        position.replaceCriteria(seed.criteria());
        position.replaceCompetencies(seed.competencies());
        return positions.save(position);
    }

    /**
     * The chart a fresh brief opens on: the manager the template names, and the mandate's own seat
     * beneath it. The third tier is left empty rather than filled with placeholder boxes — the canvas
     * offers a seat to add, which says the same thing without inventing a report nobody named.
     */
    private static List<PositionOrgNode> seededChart(String managerTitle) {
        UUID managerId = UUID.randomUUID();
        // The mandate seat leads the list — see Position#mandateSeatFirst for why that matters.
        return List.of(
                PositionOrgNode.mandateSeat(UUID.randomUUID(), managerId),
                PositionOrgNode.of(managerId, null, managerTitle, null, false, null, null));
    }

    private String hqCountryOf(UUID clientId, UUID workspaceId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .map(Client::getHqCountry)
                .orElse(null);
    }
}
