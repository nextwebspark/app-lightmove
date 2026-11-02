package app.lightmove.api.dataexport.service;

import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.candidate.model.PoolPersonExport;
import app.lightmove.api.candidate.service.CandidatePoolService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import app.lightmove.api.core.platformconfig.service.PlatformSettings;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The workspace's people as a CSV: everyone the Candidates page's filters show, or exactly the people
 * ticked. Audited like the Companies export, because the people leaving as a file is not the same act as
 * reading a page of them.
 */
@Service
@RequiredArgsConstructor
public class CandidatePoolExportService {

    private final CandidatePoolService pool;
    private final CandidatesCsvWriter writer;
    private final AuditService audit;
    private final PlatformSettings platformSettings;

    public String people(UUID userId, UUID workspaceId, PoolCriteria criteria, List<UUID> personIds,
                         HttpServletRequest httpRequest) {
        List<PoolPersonExport> people = pool.exportOf(userId, workspaceId, criteria, personIds,
                platformSettings.intValue(PlatformSetting.EXPORT_MAX_CANDIDATES));
        audit.event(ProjectEventType.CANDIDATES_EXPORTED).actor(userId).workspace(workspaceId).from(httpRequest)
                .detail("people", people.size())
                .detail("selected", personIds != null && !personIds.isEmpty())
                .record();
        return writer.write(people);
    }
}
