package app.lightmove.api.candidate.model;

import app.lightmove.api.core.email.model.EmailMessage;
import java.util.UUID;

/** The email confirming a person's answer, with the link to change their mind, sent once the answer commits. */
public record ConsentConfirmationQueued(UUID workspaceId, UUID personId, EmailMessage email) {

    @Override
    public String toString() {
        return "ConsentConfirmationQueued[person=" + personId + ", email=<redacted>]";
    }
}
