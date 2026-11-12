package app.lightmove.api.candidate.model;

import java.util.List;

/**
 * What a contact lookup answered for the email channel. An empty list is an answer (nothing on record), not a failure;
 * {@code source} names who answered, so {@code candidate} knows no vendor by name.
 */
public record FoundEmails(String source, List<CandidateEmail> emails) {

    public FoundEmails {
        emails = emails == null ? List.of() : List.copyOf(emails);
    }

    public static FoundEmails none(String source) {
        return new FoundEmails(source, List.of());
    }
}
