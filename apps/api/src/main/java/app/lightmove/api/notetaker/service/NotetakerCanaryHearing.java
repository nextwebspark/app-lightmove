package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.notetaker.dto.NotetakerCanaryHearingResponse;
import app.lightmove.api.notetaker.model.HeardLine;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Transcribes one piece the canary's bot recorded, with the notes' own transcriber, so the canary can check it heard
 * its speaker's phrase (#978). Only the canary's own service account is answered, and nothing is stored or logged
 * of what was heard. The body is read only once the caller is known, and never past the media limit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotetakerCanaryHearing {

    private final NotetakerBotIdentity bots;
    private final MeetingTranscriber transcriber;
    private final RateLimitGuard rateLimit;
    private final LightMoveProperties properties;

    public NotetakerCanaryHearingResponse hear(String identityToken, InputStream body, HttpServletRequest from)
            throws IOException {
        rateLimit.checkNotetakerCanary(from);
        if (!bots.isOurCanary(identityToken)) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        long limit = properties.notetaker().maxMediaBytes();
        byte[] audio = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
        if (audio.length == 0 || audio.length > limit) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED);
        }
        List<HeardLine> lines;
        try {
            lines = transcriber.transcribe(audio);
        } catch (RuntimeException failed) {
            log.warn("The canary's piece could not be transcribed: {}", failed.getClass().getSimpleName());
            throw ApiException.of(ErrorCode.AI_UNAVAILABLE);
        }
        return new NotetakerCanaryHearingResponse(lines.stream().map(HeardLine::text)
                .collect(Collectors.joining(" ")));
    }
}
