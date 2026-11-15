package app.lightmove.api.candidate.dto;

import java.util.List;

/**
 * A position's share-with-client consents (#1267 phase 3). {@code asksToShare} says the workspace asks for consent to
 * share, so the client-visibility switch warns; {@code clientNamable} is false on a confidential search, whose emails
 * never name the client; {@code clientName} is the name a named request would use. Only people ever asked have a row.
 */
public record ShareConsentsResponse(
        boolean asksToShare,
        boolean clientNamable,
        String clientName,
        List<ShareConsentRowResponse> rows
) {}
