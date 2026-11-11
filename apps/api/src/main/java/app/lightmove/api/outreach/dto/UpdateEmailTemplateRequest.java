package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An edit of an email template, carrying the {@code version} it was read at. */
public record UpdateEmailTemplateRequest(
        @NotBlank(message = "Name this template") @Size(max = 80, message = "That name is too long") String name,
        @Size(max = 200, message = "That subject is too long") String subject,
        @NotBlank(message = "Write the email") @Size(max = 10000, message = "That email is too long") String body,
        @NotNull Long version) {}
