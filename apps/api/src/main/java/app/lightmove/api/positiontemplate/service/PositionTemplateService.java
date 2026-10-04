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
 * Tenant scoping is in the repository query, so a template id from a request body can only resolve to
 * the shared library or the caller's own workspace.
 */
@Service
@RequiredArgsConstructor
public class PositionTemplateService {

    /** Resolved by code, not position, so reordering the picker never changes what an unmatched title drafts as. */
    public static final String FALLBACK_CODE = "generic-executive";

    private final PositionTemplateRepository templates;

    /** The workspace's own templates first, then the shared library. */
    @Transactional(readOnly = true)
    public List<PositionTemplateSummary> list(UUID workspaceId) {
        return templates.findAllVisibleTo(workspaceId).stream()
                .map(PositionTemplateSummary::of)
                .toList();
    }

    /** No generic fallback, unlike {@link #matching}: an unrecognised title suggests nothing. */
    @Transactional(readOnly = true)
    public Optional<PositionTemplateSummary> suggestFor(UUID workspaceId, String roleTitle) {
        return matchingByTitle(workspaceId, roleTitle).map(PositionTemplateSummary::of);
    }

    public PositionTemplate require(UUID workspaceId, UUID templateId) {
        return templates.findVisibleTo(templateId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /**
     * The first template whose keyword the role title contains, else the generic fallback. Empty only
     * when the library is, and the mandate is then created with a blank brief.
     */
    public Optional<PositionTemplate> matching(UUID workspaceId, String roleTitle) {
        List<PositionTemplate> visible = templates.findAllVisibleTo(workspaceId);
        return matchingByTitle(visible, roleTitle)
                .or(() -> visible.stream()
                        .filter(PositionTemplateService::isFallback)
                        .findFirst());
    }

    /** The template a role title actually names, with no generic fallback. */
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
