package app.lightmove.api.dataexport.service;

import app.lightmove.api.candidate.model.ConsentRegisterRow;
import app.lightmove.api.candidate.service.PersonConsentService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ExportSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The consent register as a CSV (#1267): the record of who was asked what, and what they answered. Audited. */
@Service
public class ConsentRegisterExportService {

    private final PersonConsentService consents;
    private final ConsentRegisterCsvWriter writer;
    private final AuditService audit;
    private final ExportSettings caps;

    public ConsentRegisterExportService(PersonConsentService consents, ConsentRegisterCsvWriter writer,
                                        AuditService audit, LightMoveProperties properties) {
        this.consents = consents;
        this.writer = writer;
        this.audit = audit;
        this.caps = properties.export();
    }

    public String register(UUID userId, UUID workspaceId, UUID projectId, HttpServletRequest httpRequest) {
        List<ConsentRegisterRow> rows = consents.registerOf(workspaceId, projectId, caps.maxCandidates());
        audit.event(ProjectEventType.CONSENT_REGISTER_EXPORTED).actor(userId).workspace(workspaceId).from(httpRequest)
                .detail("rows", rows.size())
                .detailIfPresent("projectId", projectId == null ? null : projectId.toString())
                .record();
        return writer.write(rows);
    }
}
