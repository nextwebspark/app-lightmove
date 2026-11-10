package app.lightmove.api.account.dto;

import jakarta.validation.constraints.Size;

/** Both may be null: which the account must give is the preview's {@code proof} and {@code codeRequired}. */
public record DeleteAccountRequest(
        @Size(max = 1024) String currentPassword,
        @Size(max = 32) String code
) {

    @Override
    public String toString() {
        return "DeleteAccountRequest[currentPassword=<redacted>, code=<redacted>]";
    }
}
