package app.lightmove.api.outreach.controller;

import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.outreach.dto.MyMeetingsResponse;
import app.lightmove.api.outreach.service.MyMeetingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Home's Today: the meetings on the caller's own calendar today, in their time zone. Staff only. */
@RestController
@RequiredArgsConstructor
public class MyMeetingsController {

    private final MyMeetingsService meetings;

    @GetMapping("/api/v1/me/meetings/today")
    @PreAuthorize("@workspaceAuthorizer.staffOnly(principal)")
    public MyMeetingsResponse today(@AuthenticationPrincipal AuthPrincipal principal,
                                    @RequestParam(required = false) String tz) {
        return meetings.todayOf(principal.userId(), principal.requireWorkspaceId(), tz);
    }
}
