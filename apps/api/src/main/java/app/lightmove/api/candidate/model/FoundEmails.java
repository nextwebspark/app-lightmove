package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.function.Predicate;

/**
 * What a contact lookup answered for the email channel — {@code candidate}'s contract for what it
 * takes from a provider, the role {@link EnrichedProfile} plays for research.
 *
 * <p>An empty list is an answer, not a failure: the provider had nothing on record. {@code source}
 * names whoever answered so the drawer can say where a value came from without {@code candidate}
 * knowing any vendor by name.
 */
public record FoundEmails(String source, List<CandidateEmail> emails) {

    public FoundEmails {
        emails = emails == null ? List.of() : List.copyOf(emails);
    }

    public static FoundEmails none(String source) {
        return new FoundEmails(source, List.of());
    }

    /**
     * The address to promote onto the row: a verified work address, else any work address, else a
     * personal one. A search firm writes to someone at work, and a verified address is the one that
     * will not bounce.
     */
    public CandidateEmail primary() {
        CandidateEmail verifiedWork = firstMatching(email -> email.isWork() && email.isVerified());
        if (verifiedWork != null) {
            return verifiedWork;
        }
        CandidateEmail work = firstMatching(CandidateEmail::isWork);
        return work != null ? work : firstMatching(email -> true);
    }

    private CandidateEmail firstMatching(Predicate<CandidateEmail> rule) {
        return emails.stream().filter(rule).findFirst().orElse(null);
    }
}
