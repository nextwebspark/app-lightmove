package app.lightmove.api.notetaker.controller;

import app.lightmove.api.notetaker.dto.NotetakerHeartbeatResponse;
import app.lightmove.api.notetaker.dto.NotetakerRunCompleteRequest;
import app.lightmove.api.notetaker.dto.NotetakerRunReportRequest;
import app.lightmove.api.notetaker.service.NotetakerRunService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bot's calls about its own run. Public to the security chain: the run token, in its own header so the
 * resource server never reads it as a session's bearer, is the credential, checked by {@link NotetakerRunService}.
 */
@RestController
@RequestMapping(NotetakerRunController.BASE + "/{runId}")
@RequiredArgsConstructor
public class NotetakerRunController {

    public static final String BASE = "/api/v1/internal/notetaker/runs";
    public static final String RUN_TOKEN_HEADER = "X-Notetaker-Run-Token";

    private final NotetakerRunService runs;

    @PostMapping("/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable UUID runId, @RequestHeader(name = RUN_TOKEN_HEADER, required = false) String token,
                       @Valid @RequestBody NotetakerRunReportRequest request) {
        runs.report(runId, token, request);
    }

    @PostMapping("/heartbeat")
    public NotetakerHeartbeatResponse heartbeat(@PathVariable UUID runId,
                                                @RequestHeader(name = RUN_TOKEN_HEADER, required = false) String token) {
        return runs.heartbeat(runId, token);
    }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID runId,
                         @RequestHeader(name = RUN_TOKEN_HEADER, required = false) String token,
                         @Valid @RequestBody NotetakerRunCompleteRequest request) {
        runs.complete(runId, token, request);
    }
}
