package app.lightmove.api.feedback.dto;

import app.lightmove.api.core.email.service.EmailAddressNormaliser;
import app.lightmove.api.feedback.constant.FeedbackKind;
import app.lightmove.api.feedback.constant.FeedbackSeverity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The JSON half of a multipart report — the typed part, beside the image parts.
 *
 * <p>The {@code @Size} ceilings here are <b>structural</b>, not the product rule: the real limits are
 * {@code lightmove.feedback.max-title-length} and {@code max-message-length}, enforced in the service
 * where the message can name the configured number. These exist a layer earlier so an oversized body
 * is refused before anything reads it.
 */
public record FeedbackRequest(

        @NotNull FeedbackKind kind,

        /** Absent is read as {@link FeedbackSeverity#MEDIUM} rather than refused — it is a judgement. */
        FeedbackSeverity severity,

        @NotBlank @Size(max = 500) String title,

        @NotBlank @Size(max = 20000) String message,

        @Size(max = 20000) String stepsToReproduce,

        /**
         * Only read for a caller with no session — a signed-in reporter's address comes from their
         * token, and letting the body override it would put any address they liked on an issue.
         *
         * <p>Normalised at binding: Bean Validation runs before the service ever sees the request, so a
         * pasted address with a trailing space would be rejected by {@code @Email} while the normaliser
         * that would have trimmed it sat one layer down, unreached.
         */
        @Size(max = 254) @Email
        @JsonDeserialize(converter = EmailAddressNormaliser.class) String reporterEmail,

        @Valid FeedbackContextRequest context
) {}
