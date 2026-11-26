package app.lightmove.api.subprocessor.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.rbac.PlatformAction;
import app.lightmove.api.core.security.rbac.RequirePlatformPermission;
import app.lightmove.api.core.security.rbac.RequireRecentTwoFactor;
import app.lightmove.api.subprocessor.dto.PlatformSubprocessorNoticeResponse;
import app.lightmove.api.subprocessor.dto.PlatformSubprocessorObjectionResponse;
import app.lightmove.api.subprocessor.dto.PublishSubprocessorNoticeRequest;
import app.lightmove.api.subprocessor.service.SubprocessorNoticeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform → Sub-processors. Publishing emails every workspace's admins, so it asks a fresh two-factor code; the
 * objections are the one tenant read the console makes, each workspace's in its own scope.
 */
@RestController
@RequestMapping("/api/v1/platform/subprocessor-notices")
@RequiredArgsConstructor
public class PlatformSubprocessorNoticeController {

    private final SubprocessorNoticeService notices;

    @GetMapping
    @RequirePlatformPermission(PlatformAction.SUBPROCESSOR_NOTICE_MANAGE)
    public List<PlatformSubprocessorNoticeResponse> list() {
        return notices.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequireRecentTwoFactor(PlatformAction.SUBPROCESSOR_NOTICE_MANAGE)
    public PlatformSubprocessorNoticeResponse publish(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @Valid @RequestBody PublishSubprocessorNoticeRequest request,
                                                      HttpServletRequest httpRequest) {
        return notices.publish(principal.userId(), request, httpRequest);
    }

    @GetMapping("/{noticeId}/objections")
    @RequirePlatformPermission(PlatformAction.SUBPROCESSOR_NOTICE_MANAGE)
    public List<PlatformSubprocessorObjectionResponse> objections(@PathVariable UUID noticeId) {
        return notices.objectionsTo(noticeId);
    }
}
