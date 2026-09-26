package app.lightmove.api.positiontemplate.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.positiontemplate.dto.PositionTemplateSummary;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.repository.PositionTemplateRepository;
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
 * <p>{@link #require}, {@link #matching} and {@link #suggestFor} are the seam {@code position} drafts
 * and suggests a brief through. Nothing here reads a brief.
 */
@Service
@RequiredArgsConstructor
public class PositionTemplateService {

    /**
     * The template a title falls back to when no keyword matches. Resolved by code rather than by
     * position: the library is ordered for a picker, and reordering it must not change what an
     * unrecognised mandate is drafted as.
     */
    public static final String FALLBACK_CODE = "generic-executive";

    private final PositionTemplateRepository templates;

    /** The picker's options: the workspace's own templates first, then the shared library. */
    @Transactional(readOnly = true)
    public List<PositionTemplateSummary> list(UUID workspaceId) {
        return templates.findAllVisibleTo(workspaceId).stream()
                .map(PositionTemplateSummary::of)
                .toList();
    }

    /**
     * The workspace's matching brief template for a role title, offered as a whole-brief opt-in
     * rather than applied. No generic fallback, unlike {@link #matching}: an unrecognised title
     * suggests nothing rather than silently suggesting generic-executive as though it were a real
     * match.
     */
    @Transactional(readOnly = true)
    public Optional<PositionTemplateSummary> suggestFor(UUID workspaceId, String roleTitle) {
        return matchingByTitle(workspaceId, roleTitle).map(PositionTemplateSummary::of);
    }

    public PositionTemplate require(UUID workspaceId, UUID templateId) {
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
    public Optional<PositionTemplate> matching(UUID workspaceId, String roleTitle) {
        List<PositionTemplate> visible = templates.findAllVisibleTo(workspaceId);
        return matchingByTitle(visible, roleTitle)
                .or(() -> visible.stream()
                        .filter(PositionTemplateService::isFallback)
                        .findFirst());
    }

    /**
     * The template a role title actually names, with no generic fallback — for a caller that would
     * rather have nothing than a wrong answer, unlike {@link #matching}, whose fallback exists for
     * drafting a whole brief.
     */
    public Optional<PositionTemplate> matchingByTitle(UUID workspaceId, String roleTitle) {
        return matchingByTitle(templates.findAllVisibleTo(workspaceId), roleTitle);
    }

    /** The one template that can be neither archived nor hidden, since every unmatched title needs it. */
    static boolean isFallback(PositionTemplate template) {
        return FALLBACK_CODE.equals(template.getCode());
    }

    private static Optional<PositionTemplate> matchingByTitle(List<PositionTemplate> visible, String roleTitle) {
        return visible.stream().filter(template -> template.matchesTitle(roleTitle)).findFirst();
    }
}
