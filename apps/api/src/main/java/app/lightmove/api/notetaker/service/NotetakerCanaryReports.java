package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.dto.NotetakerCanaryReportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A canary's report, taken only from our own bot: anyone else could switch a platform's calls to the backup. */
@Service
@RequiredArgsConstructor
public class NotetakerCanaryReports {

    private final NotetakerBotIdentity bots;
    private final NotetakerPlatformHealth health;

    public void receive(String identityToken, NotetakerCanaryReportRequest report) {
        if (!bots.isOurBot(identityToken)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        health.record(report.platform(), report.healthy(), report.reason());
    }
}
