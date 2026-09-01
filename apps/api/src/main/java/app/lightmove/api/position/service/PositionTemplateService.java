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
 * <p>Until V42 this was {@code PositionTemplates}, a Java constant holding seven roles. Moving it into
 * the database is what lets a firm eventually own its own versions, and what makes "draft this brief
 * as a Chief Compliance Officer" a choice on the screen rather than a consequence of how the mandate
 * happened to be named.
 *
 * <p><b>Tenant scoping is in the query, not here.</b> Both reads take the workspace from the caller's
 * principal and the repository answers with the shared library plus that workspace's own — so a
 * template id arriving in a request body can only ever resolve to something the caller may see.
 *
 * <p>Only {@link #list} is public. Resolving an entity is for this package's own seeding and apply
 * paths, which is why those two methods stay package-private rather than becoming a seam anything
 * outside {@code position} could pull on.
 */
@Service
@RequiredArgsConstructor
public class PositionTemplateService {

    /**
     * The template a title falls back to when no keyword matches. Resolved by code rather than by
     * position in the list: the library is ordered for a picker, and an editor reordering it must not
     * silently change what an unrecognised mandate is drafted as.
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
     * <p>Empty only when the library itself is — a database seeded before V42, or one whose templates
     * have all been deactivated. A mandate is still created in that case, with a blank brief, because
     * failing project creation over missing reference content would be the worse answer.
     */
    Optional<PositionTemplate> matching(UUID workspaceId, String roleTitle) {
        List<PositionTemplate> visible = templates.findAllVisibleTo(workspaceId);
        return visible.stream()
                .filter(template -> template.matchesTitle(roleTitle))
                .findFirst()
                .or(() -> visible.stream()
                        .filter(template -> FALLBACK_CODE.equals(template.getCode()))
                        .findFirst());
    }
}
