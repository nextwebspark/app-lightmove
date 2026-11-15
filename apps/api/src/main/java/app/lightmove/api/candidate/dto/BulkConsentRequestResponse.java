package app.lightmove.api.candidate.dto;

import java.util.List;

/** How many were asked, and who was not and why: nobody is dropped without saying so. */
public record BulkConsentRequestResponse(int sent, List<ConsentRequestSkipResponse> skipped) {}
