package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.audit.repository.AuditEventRepository;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.project.dto.ProjectActivityEntryResponse;
import app.lightmove.api.project.dto.ProjectActivityResponse;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's recent activity for the projects list's side panel, read off the audit trail.
 *
 * <p>An allowlist in both directions: only the events that describe the work (companies, people, the
 * brief, the market) and only the metadata keys the panel phrases. Team and access changes, contact
 * lookups and column housekeeping stay in the ledger — the first because who was let into a mandate is
 * not a progress line, the rest because they are noise beside it. Request-level fields (IP, user agent)
 * are never read at all.
 */
@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private static final Set<ProjectEventType> SHOWN = EnumSet.of(
            ProjectEventType.PROJECT_CREATED,
            ProjectEventType.POSITION_UPDATED,
            ProjectEventType.POSITION_TEMPLATE_APPLIED,
            ProjectEventType.POSITION_PUBLISHED,
            ProjectEventType.POSITION_DOCUMENT_ATTACHED,
            ProjectEventType.STRATEGY_UPDATED,
            ProjectEventType.STRATEGY_SEARCH_SAVED,
            ProjectEventType.TRIAGE_COMPANY_ADDED,
            ProjectEventType.TRIAGE_COMPANY_CAPTURED,
            ProjectEventType.TRIAGE_BULK_ADDED,
            ProjectEventType.TRIAGE_COMPANY_REMOVED,
            ProjectEventType.CANDIDATE_ADDED,
            ProjectEventType.CANDIDATE_REMOVED,
            ProjectEventType.SPREADSHEET_IMPORTED,
            ProjectEventType.COMPANIES_EXPORTED);

    /** Shown only when they record a status: the same types also cover note and profile edits. */
    private static final Set<ProjectEventType> SHOWN_WHEN_STATUS_CHANGED = EnumSet.of(
            ProjectEventType.TRIAGE_COMPANY_MOVED,
            ProjectEventType.CANDIDATE_UPDATED);

    private static final List<String> DETAIL_KEYS = List.of(
            "status", "added", "companyName", "fullName", "fileName",
            "companiesCreated", "candidatesCreated", "stage");

    private final AuditEventRepository events;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public ProjectActivityResponse list(UUID workspaceId, UUID projectId, Long beforeCursor, int pageSize) {
        int size = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        List<AuditEvent> page = events.findLatestForTarget(
                workspaceId, AuditService.PROJECT_TARGET, projectId.toString(),
                codesOf(SHOWN), codesOf(SHOWN_WHEN_STATUS_CHANGED),
                beforeCursor == null ? Long.MAX_VALUE : beforeCursor, size + 1);

        boolean hasMore = page.size() > size;
        List<AuditEvent> shown = hasMore ? page.subList(0, size) : page;

        Map<UUID, User> actorById = users.findAllById(shown.stream()
                        .map(AuditEvent::getActorUserId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ProjectActivityEntryResponse> entries = shown.stream()
                .map(event -> {
                    User actor = event.getActorUserId() == null ? null : actorById.get(event.getActorUserId());
                    return new ProjectActivityEntryResponse(
                            event.getId(), event.getEventType(), event.getOccurredAt(),
                            event.getActorUserId(),
                            actor == null ? null : actor.getFullName(),
                            actor == null ? null : actor.getAvatarUrl(),
                            detailsOf(event.getMetadata()));
                })
                .toList();
        return new ProjectActivityResponse(entries, hasMore ? shown.getLast().getId() : null);
    }

    private static Map<String, String> detailsOf(Map<String, Object> metadata) {
        Map<String, String> details = new LinkedHashMap<>();
        for (String key : DETAIL_KEYS) {
            Object value = metadata.get(key);
            if (value != null) {
                details.put(key, value.toString());
            }
        }
        return details;
    }

    private static List<String> codesOf(Set<ProjectEventType> types) {
        return types.stream().map(ProjectEventType::code).toList();
    }
}
