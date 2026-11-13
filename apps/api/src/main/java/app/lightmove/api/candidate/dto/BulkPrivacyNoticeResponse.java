package app.lightmove.api.candidate.dto;

import java.util.List;

/** How many were sent a notice, and who was not and why: nobody is dropped without saying so. */
public record BulkPrivacyNoticeResponse(int sent, List<PrivacyNoticeSkipResponse> skipped) {}
