package app.lightmove.api.outreach.controller;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.outreach.constant.ConversationScope;
import app.lightmove.api.outreach.dto.MyOutreachQueueResponse;
import app.lightmove.api.outreach.service.MyOutreachQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Home's outreach queue: what waits on the caller, or on the teams they lead, across their positions. Staff only. */
@RestController
@RequiredArgsConstructor
public class MyOutreachQueueController {

    private final MyOutreachQueueService queue;

    @GetMapping("/api/v1/me/outreach/needs")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public MyOutreachQueueResponse needs(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(required = false) String who) {
        return queue.needsOf(principal.userId(), principal.requireWorkspaceId(),
                ApiValueEnum.parse(ConversationScope.class, who, ConversationScope.MINE, "conversation scope"));
    }
}
