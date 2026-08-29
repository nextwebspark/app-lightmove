package app.lightmove.api.core.llm.controller;

import app.lightmove.api.core.llm.dto.EmbedRequest;
import app.lightmove.api.core.llm.dto.EmbedResponse;
import app.lightmove.api.core.llm.dto.ShortlistRequest;
import app.lightmove.api.core.llm.dto.ShortlistResponse;
import app.lightmove.api.core.llm.service.CandidateEmbeddingService;
import app.lightmove.api.core.llm.service.CandidateShortlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves the Google GenAI integration end to end: a chat call scoped by the recruiter system
 * prompt, and a raw embedding call. Stateless — no workspace data is read or written, so unlike
 * every other controller here this needs no {@code @PreAuthorize} action; being authenticated
 * (already the default for everything under {@code /api/v1}) is the whole gate.
 */
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class LlmController {

    private final CandidateShortlistService shortlistService;
    private final CandidateEmbeddingService embeddingService;

    @PostMapping("/shortlist")
    public ResponseEntity<ShortlistResponse> shortlist(@Valid @RequestBody ShortlistRequest request) {
        String verdict = shortlistService.shortlist(request.jobBrief(), request.candidateProfile());
        return ResponseEntity.ok(new ShortlistResponse(verdict));
    }

    @PostMapping("/embed")
    public ResponseEntity<EmbedResponse> embed(@Valid @RequestBody EmbedRequest request) {
        float[] vector = embeddingService.embed(request.text());
        return ResponseEntity.ok(new EmbedResponse(vector.length, vector));
    }
}
