package app.lightmove.api.candidate.dto;

import java.util.UUID;

/** One person a bulk send left out; {@code reason} is a {@code PrivacyNoticeSkipReason} wire token. */
public record PrivacyNoticeSkipResponse(UUID personId, String reason) {}
