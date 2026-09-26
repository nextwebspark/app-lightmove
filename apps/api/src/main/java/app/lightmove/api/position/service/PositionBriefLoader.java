package app.lightmove.api.position.service;

import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.repository.PositionRepository;
import app.lightmove.api.positiontemplate.service.PositionTemplateService;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the brief behind a mandate, drafting one when there is none. Every load goes through the
 * project's {@code (id, workspaceId)} lookup, so a foreign mandate 404s before any brief row is touched.
 * Drafting runs on two paths — at creation, and lazily on first read — and must behave the same on both.
 */
@Component
@RequiredArgsConstructor
class PositionBriefLoader {

    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final ClientRepository clients;
    private final PositionTemplateService templates;

    PositionBrief require(UUID workspaceId, UUID projectId) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);
        Position position = positions.findByProjectId(project.getId())
                .orElseGet(() -> draft(workspaceId, project.getId(), project.getPositionTitle(),
                        hqCountryOf(project.getClientId(), workspaceId)));
        return new PositionBrief(project, position);
    }

    /** For a reader that must not write: an undrafted brief answers empty rather than being drafted. */
    Optional<Position> find(UUID workspaceId, UUID projectId) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);
        return positions.findByProjectId(project.getId());
    }

    /** An undrafted brief reads blank and is not saved. */
    PositionBrief read(UUID workspaceId, UUID projectId) {
        Project project = projects.requireInWorkspace(projectId, workspaceId);
        Position position = positions.findByProjectId(project.getId())
                .orElseGet(() -> Position.forProject(project.getId(), null));
        return new PositionBrief(project, position);
    }

    /** The matched template's brief, at the client's home country; an empty catalog drafts a blank brief. */
    Position draft(UUID workspaceId, UUID projectId, String positionTitle, String hqCountry) {
        Position position = Position.forProject(projectId, hqCountry);
        templates.matching(workspaceId, positionTitle)
                .ifPresent(template -> PositionTemplateApplier.applyTo(position, template));
        return positions.save(position);
    }

    private String hqCountryOf(UUID clientId, UUID workspaceId) {
        return clients.findByIdAndWorkspaceId(clientId, workspaceId)
                .map(Client::getHqCountry)
                .orElse(null);
    }
}
