package app.lightmove.api.core.security.model;

/**
 * What signup step 1 supplies. A command, not the HTTP DTO — the application layer should not know
 * that an HTTP request exists.
 */
public record SignupCommand(
        String fullName,
        String email,
        String password,
        boolean termsAccepted
) {

    @Override
    public String toString() {
        // These land in logs by accident. Never with the password in them.
        return "SignupCommand[email=%s, fullName=%s, password=***]".formatted(email, fullName);
    }
}
