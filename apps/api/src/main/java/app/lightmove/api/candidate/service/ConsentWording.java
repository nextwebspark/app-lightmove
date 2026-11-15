package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ConsentPurpose;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * What a person reads about each purpose (#1267), word for word with the mockups and the SPA's preview
 * ({@code consentPreview.ts}). Each answer keeps the page's sentence and {@link #VERSION} it was given under, so a
 * later change of words never rewrites one.
 */
public final class ConsentWording {

    public static final String VERSION = "2026-11-16";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);
    private static final String CONFIDENTIAL_CLIENT = "a confidential client";

    private ConsentWording() {
    }

    /** The page's sentence under a purpose's heading; the one an answer keeps. */
    public static String sentenceOf(ConsentPurpose purpose, int keepOnFileMonths, String clientName, String roleTitle) {
        return switch (purpose) {
            case KEEP_ON_FILE -> "We would keep your details for %d months, so we can think of you when a suitable role "
                    .formatted(keepOnFileMonths) + "comes up.";
            case SHARE_WITH_CLIENT -> "We would like to send your profile to %s for a %s role."
                    .formatted(clientOf(clientName), roleTitle);
            case MARKETING -> "Occasional emails from us about roles and the market. You can stop them at any time.";
        };
    }

    /** A renewal's sentence: the day they agreed, and the day a yes now keeps them until. */
    public static String renewalSentenceOf(Instant agreedAt, Instant renewedUntil) {
        return "You agreed on %s that we could keep your details on file for future roles. If you say yes, we keep them until %s."
                .formatted(dayOf(agreedAt), dayOf(renewedUntil));
    }

    /** One bullet of a request email, after "We'd like your permission to:". */
    public static String askingOf(ConsentPurpose purpose, int keepOnFileMonths, String clientName, String roleTitle) {
        return switch (purpose) {
            case KEEP_ON_FILE -> "keep your details on file for future roles, for %d months".formatted(keepOnFileMonths);
            case SHARE_WITH_CLIENT -> "share your profile with %s for a %s role".formatted(clientOf(clientName), roleTitle);
            case MARKETING -> "send you career and market updates";
        };
    }

    /** How a purpose is named beside its answer, on the confirmation and the manage page. */
    public static String labelOf(ConsentPurpose purpose, String clientName, String roleTitle) {
        return switch (purpose) {
            case KEEP_ON_FILE -> "Keep your details on file for future roles";
            case SHARE_WITH_CLIENT -> "Share your profile with %s for a %s role".formatted(clientOf(clientName), roleTitle);
            case MARKETING -> "Career and market updates";
        };
    }

    public static String dayOf(Instant instant) {
        return DAY.format(instant);
    }

    private static String clientOf(String clientName) {
        return clientName == null ? CONFIDENTIAL_CLIENT : clientName;
    }
}
