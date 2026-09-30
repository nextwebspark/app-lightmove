package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.ContactEntry;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.text.service.LinkedInUrls;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether an executive a mandate is filing is someone the workspace already knows — on a key that
 * identifies a human rather than describes one. The LinkedIn profile first; then an email address,
 * unless the two records name different profiles, since one address shared by two profiles is a
 * shared inbox, not one person.
 *
 * <p>Not a phone: a switchboard number is on every executive of a company. Not a name: two people share
 * one, and folding them silently is the one mistake a merge tool cannot see afterwards.
 */
@Component
@RequiredArgsConstructor
class PersonMatcher {

    private static final Comparator<Person> OLDEST_FIRST =
            Comparator.comparing(Person::getCreatedAt).thenComparing(Person::getId);

    private final PersonRepository people;

    Optional<Person> find(UUID workspaceId, CandidateDetails details) {
        String slug = LinkedInUrls.profileSlugOrNull(details.linkedinUrl());
        if (slug != null) {
            Optional<Person> byProfile = people.findByWorkspaceIdAndProfileSlugLike(workspaceId, slug).stream()
                    .filter(person -> slug.equals(slugOf(person)))
                    .min(OLDEST_FIRST);
            if (byProfile.isPresent()) {
                return byProfile;
            }
        }
        for (ContactEntry email : details.emails()) {
            String key = CandidateContact.keyOf(ContactChannel.EMAIL, email.value());
            if (key.isEmpty()) {
                continue;
            }
            Optional<Person> byEmail = people.findByWorkspaceIdAndEmailKey(workspaceId, key).stream()
                    .filter(person -> slug == null || slugOf(person) == null || slug.equals(slugOf(person)))
                    .min(OLDEST_FIRST);
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a person other than {@code personId} is already the profile {@code linkedinUrl} names.
     * An edit writes the URL onto the workspace's person, so a mandate-scoped check cannot guard it:
     * two people on one slug would leave every later capture of it mapped to whichever is older.
     */
    boolean isHeldByAnother(UUID workspaceId, String linkedinUrl, UUID personId) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        return slug != null && people.findByWorkspaceIdAndProfileSlugLike(workspaceId, slug).stream()
                .anyMatch(person -> !person.getId().equals(personId) && slug.equals(slugOf(person)));
    }

    private static String slugOf(Person person) {
        return LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl());
    }
}
