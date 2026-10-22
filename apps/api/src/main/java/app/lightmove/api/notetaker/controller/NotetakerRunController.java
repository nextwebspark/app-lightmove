package app.lightmove.api.notetaker.controller;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.constant.MeetingMediaKind;
import app.lightmove.api.notetaker.dto.NotetakerHeartbeatResponse;
import app.lightmove.api.notetaker.dto.NotetakerRunReportRequest;
import app.lightmove.api.notetaker.service.NotetakerRunService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    public static final String PIECE_STARTED_AT_HEADER = "X-Notetaker-Piece-Started-At";

    private final NotetakerRunService runs;

    @PostMapping("/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable UUID runId, @RequestHeader(name = RUN_TOKEN_HEADER, required = false) String token,
                       @Valid @RequestBody NotetakerRunReportRequest request, HttpServletRequest httpRequest) {
        runs.report(runId, token, request, httpRequest);
    }

    @PostMapping("/heartbeat")
    public NotetakerHeartbeatResponse heartbeat(@PathVariable UUID runId,
                                                @RequestHeader(name = RUN_TOKEN_HEADER, required = false) String token,
                                                HttpServletRequest httpRequest) {
        return runs.heartbeat(runId, token, httpRequest);
    }

    /**
     * One 30-second piece as the raw body: audio as WebM, captions as the bot's JSON lines. An audio piece says when
     * it began; one that does not is taken to have begun as it arrived.
     */
    @PutMapping("/media/{kind}/{sequence}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void storeMedia(@PathVariable UUID runId, @PathVariable String kind, @PathVariable int sequence,
                           @RequestHeader(name = RUN_TOKEN_HEADER, required = false) String token,
                           @RequestHeader(name = PIECE_STARTED_AT_HEADER, required = false) String startedAt,
                           HttpServletRequest httpRequest) throws IOException {
        MeetingMediaKind mediaKind = MeetingMediaKind.fromPath(kind)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        runs.storeMedia(runId, token, mediaKind, sequence, startedAt, httpRequest.getInputStream(),
                httpRequest.getContentLengthLong(), httpRequest);
    }

    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID runId,
                         @RequestHeader(name = RUN_TOKEN_HEADER, required = false) String token,
                         HttpServletRequest httpRequest) {
        runs.complete(runId, token, httpRequest);
    }
}
