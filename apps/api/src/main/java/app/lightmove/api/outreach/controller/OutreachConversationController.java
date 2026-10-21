package app.lightmove.api.outreach.controller;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.core.security.rbac.RequireProjectPermission;
import app.lightmove.api.outreach.constant.ConversationScope;
import app.lightmove.api.outreach.constant.ConversationView;
import app.lightmove.api.outreach.dto.ConversationListResponse;
import app.lightmove.api.outreach.dto.ConversationResponse;
import app.lightmove.api.outreach.service.ConversationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The Outreach page's queue and one executive's conversation on a position. {@code WORK_EXECUTE}. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/outreach/conversations")
@RequiredArgsConstructor
public class OutreachConversationController {

    private final ConversationService conversations;

    @GetMapping
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ConversationListResponse list(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable UUID projectId,
                                         @RequestParam(required = false) String view,
                                         @RequestParam(required = false) String who,
                                         @RequestParam(required = false) String q) {
        return conversations.list(principal.userId(), principal.requireWorkspaceId(), projectId,
                ApiValueEnum.parse(ConversationView.class, view, ConversationView.NEEDS, "conversation view"),
                ApiValueEnum.parse(ConversationScope.class, who, ConversationScope.MINE, "conversation scope"), q);
    }

    @GetMapping("/{candidateId}")
    @RequireProjectPermission(ProjectAction.WORK_EXECUTE)
    public ConversationResponse conversation(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable UUID projectId, @PathVariable UUID candidateId) {
        return conversations.conversationOf(principal.userId(), principal.requireWorkspaceId(), projectId,
                candidateId);
    }
}
