package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.EmbedRequest;
import app.lightmove.api.candidate.dto.EmbedResponse;
import app.lightmove.api.candidate.dto.ShortlistRequest;
import app.lightmove.api.candidate.dto.ShortlistResponse;
import app.lightmove.api.candidate.service.CandidateEmbeddingService;
import app.lightmove.api.candidate.service.CandidateShortlistService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimiter;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the Google GenAI integration end to end: a chat call scoped by the recruiter system
 * prompt, and a raw embedding call. Stateless — no workspace data is read or written, so unlike
 * every other controller here this needs no {@code @PreAuthorize} action; being authenticated
 * (already the default for everything under {@code /api/v1}) is the whole gate. Both calls are
 * billed Vertex AI usage, so each is additionally capped per user — see {@link LlmRateLimitSettings}.
 */
@RestController
@RequestMapping("/api/v1/llm")
public class CandidateLlmController {

    private final CandidateShortlistService shortlistService;
    private final CandidateEmbeddingService embeddingService;
    private final RateLimiter rateLimiter;
    private final LlmRateLimitSettings rateLimitSettings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, the one case the Lombok rule exempts.
    public CandidateLlmController(CandidateShortlistService shortlistService,
                                  CandidateEmbeddingService embeddingService,
                                  RateLimiter rateLimiter,
                                  LightMoveProperties properties) {
        this.shortlistService = shortlistService;
        this.embeddingService = embeddingService;
        this.rateLimiter = rateLimiter;
        this.rateLimitSettings = properties.llm().rateLimit();
    }

    @PostMapping("/shortlist")
    public ResponseEntity<ShortlistResponse> shortlist(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody ShortlistRequest request) {
        checkRateLimit("shortlist", principal, rateLimitSettings.shortlistRequestsPerMinute());
        String verdict = shortlistService.shortlist(request.jobBrief(), request.candidateProfile());
        return ResponseEntity.ok(new ShortlistResponse(verdict));
    }

    @PostMapping("/embed")
    public ResponseEntity<EmbedResponse> embed(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody EmbedRequest request) {
        checkRateLimit("embed", principal, rateLimitSettings.embedRequestsPerMinute());
        float[] vector = embeddingService.embed(request.text());
        return ResponseEntity.ok(new EmbedResponse(vector.length, vector));
    }

    /**
     * Both endpoints call billed Vertex AI usage with only authentication as a gate, so each is
     * additionally capped per user — a coarse brake against an authenticated caller looping either
     * endpoint and running up the GCP project's bill.
     */
    private void checkRateLimit(String action, AuthPrincipal principal, int limit) {
        if (!rateLimitSettings.enabled()) {
            return;
        }
        boolean withinBudget = rateLimiter.tryAcquire(
                "llm-%s:user:%s".formatted(action, principal.userId()), limit, Duration.ofMinutes(1));
        if (!withinBudget) {
            throw ApiException.of(ErrorCode.RATE_LIMITED);
        }
    }
}
