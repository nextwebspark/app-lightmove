package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.HiringSide;
import app.lightmove.api.common.persona.model.HiringCompanyProfile;
import app.lightmove.api.project.service.ClientService;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.Firm;
import app.lightmove.api.workspace.service.FirmService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Settles whose profile the assistant reads for a mandate: the client's at an agency; in-house, the business unit's
 * read against the firm's, or the firm's alone while the unit has recorded nothing.
 */
@Service
@RequiredArgsConstructor
public class HiringSideResolver {

    private final FirmService firms;
    private final ClientService clients;

    public HiringSide resolve(UUID workspaceId, UUID projectId) {
        Firm firm = firms.firmOf(workspaceId);
        HiringCompanyProfile client = clients.hiringProfileOfProject(workspaceId, projectId);
        if (firm.mode() == WorkspaceMode.AGENCY) {
            return new HiringSide(firm, client, null);
        }
        return client.persona() == null || client.persona().hasNothingRecorded()
                ? new HiringSide(firm, firm.profile(), null)
                : new HiringSide(firm, client.within(firm.profile()), firm.profile());
    }
}
