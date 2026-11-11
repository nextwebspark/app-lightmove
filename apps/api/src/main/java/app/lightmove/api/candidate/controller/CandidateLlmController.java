package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.EmbedRequest;
import app.lightmove.api.candidate.dto.EmbedResponse;
import app.lightmove.api.candidate.dto.ShortlistRequest;
import app.lightmove.api.candidate.dto.ShortlistResponse;
import app.lightmove.api.candidate.service.CandidateEmbeddingService;
import app.lightmove.api.candidate.service.CandidateShortlistService;
import app.lightmove.api.core.config.LlmRateLimitSettings;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the Google GenAI integration (a chat and an embedding call). Reads no workspace data, so authentication alone
 * gates it; both are billed, so each is capped per user by {@link LlmBudgetGuard} ({@link LlmRateLimitSettings}).
 */
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class CandidateLlmController {

    private final CandidateShortlistService shortlistService;
    private final CandidateEmbeddingService embeddingService;
    private final LlmBudgetGuard llmBudget;

    @PostMapping("/shortlist")
    public ShortlistResponse shortlist(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody ShortlistRequest request) {
        llmBudget.require(LlmBudget.SHORTLIST, principal.userId());
        String verdict = shortlistService.shortlist(request.jobBrief(), request.candidateProfile());
        return new ShortlistResponse(verdict);
    }

    @PostMapping("/embed")
    public EmbedResponse embed(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody EmbedRequest request) {
        llmBudget.require(LlmBudget.EMBED, principal.userId());
        float[] vector = embeddingService.embed(request.text());
        return new EmbedResponse(vector.length, vector);
    }
}
