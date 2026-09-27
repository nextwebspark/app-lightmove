package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.HiringSide;
import app.lightmove.api.project.service.ClientService;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.Firm;
import app.lightmove.api.workspace.service.FirmService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Settles whose profile the assistant reads for a mandate: the firm's in-house, the client's for an agency. */
@Service
@RequiredArgsConstructor
public class HiringSideResolver {

    private final FirmService firms;
    private final ClientService clients;

    public HiringSide resolve(UUID workspaceId, UUID projectId) {
        Firm firm = firms.firmOf(workspaceId);
        return firm.mode() == WorkspaceMode.AGENCY
                ? new HiringSide(firm, clients.hiringProfileOfProject(workspaceId, projectId))
                : new HiringSide(firm, firm.profile());
    }
}
