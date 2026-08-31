package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.EmbedRequest;
import app.lightmove.api.candidate.dto.EmbedResponse;
import app.lightmove.api.candidate.dto.ShortlistRequest;
import app.lightmove.api.candidate.dto.ShortlistResponse;
import app.lightmove.api.candidate.service.CandidateEmbeddingService;
import app.lightmove.api.candidate.service.CandidateShortlistService;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * billed Vertex AI usage, so each is additionally capped per user by {@link LlmBudgetGuard} — a
 * coarse brake against an authenticated caller looping either endpoint and running up the GCP bill.
 * Budgets are {@link LlmRateLimitSettings}.
 */
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class CandidateLlmController {

    private final CandidateShortlistService shortlistService;
    private final CandidateEmbeddingService embeddingService;
    private final LlmBudgetGuard llmBudget;

    @PostMapping("/shortlist")
    public ResponseEntity<ShortlistResponse> shortlist(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @Valid @RequestBody ShortlistRequest request) {
        llmBudget.checkShortlist(principal.userId());
        String verdict = shortlistService.shortlist(request.jobBrief(), request.candidateProfile());
        return ResponseEntity.ok(new ShortlistResponse(verdict));
    }

    @PostMapping("/embed")
    public ResponseEntity<EmbedResponse> embed(@AuthenticationPrincipal AuthPrincipal principal,
                                               @Valid @RequestBody EmbedRequest request) {
        llmBudget.checkEmbed(principal.userId());
        float[] vector = embeddingService.embed(request.text());
        return ResponseEntity.ok(new EmbedResponse(vector.length, vector));
    }
}
