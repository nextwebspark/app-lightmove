package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.notetaker.dto.NotetakerCanaryReportRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * A canary's report, taken only from the canary's own service account: anyone else — a meeting bot included — could
 * switch a platform's calls to the backup, or hide an outage.
 */
@Service
@RequiredArgsConstructor
public class NotetakerCanaryReports {

    private final NotetakerBotIdentity bots;
    private final NotetakerPlatformHealth health;
    private final RateLimitGuard rateLimit;

    public void receive(String identityToken, NotetakerCanaryReportRequest report, HttpServletRequest from) {
        rateLimit.checkNotetakerCanary(from);
        if (!bots.isOurCanary(identityToken)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        health.record(report.platform(), report.healthy(), report.reason());
    }
}
