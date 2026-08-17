package app.lightmove.api.core.security.model;

/**
 * What Settings → Profile supplies. A command, not the HTTP DTO — the application layer should not
 * know that an HTTP request exists.
 *
 * <p>Only the fields their holder owns: the email is the identity, and the role is an admin's
 * decision, so neither is here. See {@code UpdateProfileRequest}.
 */
public record ProfileUpdateCommand(
        String fullName,
        String title,
        String timezone,
        String locale
) {
}
