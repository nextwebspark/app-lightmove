package app.lightmove.api.candidate.model;

import java.util.UUID;

/** A privacy link just written: its row's id, and the raw token only the email ever carries. */
public record MintedPrivacyLink(UUID id, String token) {

    @Override
    public String toString() {
        return "MintedPrivacyLink[id=" + id + ", token=<redacted>]";
    }
}
