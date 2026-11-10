package app.lightmove.api.account.dto;

import java.time.Instant;

/** What the person agreed to at signup, and to which version of the privacy policy. */
public record ExportedConsents(Instant termsAcceptedAt, String privacyPolicyVersion) {}
