package app.lightmove.api.position.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.dto.PositionTemplateSummary;
import app.lightmove.api.position.model.PositionTemplate;
import app.lightmove.api.position.repository.PositionTemplateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The role-template catalog: which templates a workspace can see, and which one a role title lands on.
 *
 * <p><b>Tenant scoping is in the query, not here.</b> Both reads take the workspace from the caller's
 * principal and the repository answers with the shared library plus that workspace's own, so a
 * template id arriving in a request body can only resolve to something the caller may see.
 *
 * <p>Only {@link #list} is public; resolving an entity is for this package's own seeding and apply
 * paths.
 */
@Service
@RequiredArgsConstructor
public class PositionTemplateService {

    /**
     * The template a title falls back to when no keyword matches. Resolved by code rather than by
     * position: the library is ordered for a picker, and reordering it must not change what an
     * unrecognised mandate is drafted as.
     */
    private static final String FALLBACK_CODE = "generic-executive";

    private final PositionTemplateRepository templates;

    /** The picker's options: the workspace's own templates first, then the shared library. */
    @Transactional(readOnly = true)
    public List<PositionTemplateSummary> list(UUID workspaceId) {
        return templates.findAllVisibleTo(workspaceId).stream()
                .map(PositionTemplateSummary::of)
                .toList();
    }

    PositionTemplate require(UUID workspaceId, UUID templateId) {
        return templates.findVisibleTo(templateId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * The template a new mandate's brief is drafted from: the first whose keyword the role title
     * contains, else the generic fallback.
     *
     * <p>Empty only when the library itself is. A mandate is still created in that case, with a blank
     * brief, rather than failing project creation over missing reference content.
     */
    Optional<PositionTemplate> matching(UUID workspaceId, String roleTitle) {
        List<PositionTemplate> visible = templates.findAllVisibleTo(workspaceId);
        return keywordMatch(visible, roleTitle)
                .or(() -> visible.stream()
                        .filter(template -> FALLBACK_CODE.equals(template.getCode()))
                        .findFirst());
    }

    /**
     * The template to offer alongside a proposed role title — a genuine keyword match only, never the
     * generic fallback: "this reads like a Generic Executive mandate" would not read as a real
     * suggestion, so an unrecognised title suggests nothing rather than the same catch-all every
     * unrecognised title would land on.
     */
    @Transactional(readOnly = true)
    public Optional<PositionTemplateSummary> suggestFor(UUID workspaceId, String roleTitle) {
        return keywordMatch(templates.findAllVisibleTo(workspaceId), roleTitle)
                .map(PositionTemplateSummary::of);
    }

    private static Optional<PositionTemplate> keywordMatch(List<PositionTemplate> visible, String roleTitle) {
        return visible.stream().filter(template -> template.matchesTitle(roleTitle)).findFirst();
    }
}
