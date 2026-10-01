package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ProfileClaim;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.ContactEntry;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.text.service.LinkedInUrls;
import java.util.Comparator;
import java.util.List;
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
            Optional<Person> byProfile = people.findByWorkspaceIdAndProfileSlug(workspaceId, slug);
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
     * Who the workspace holds by the same name at the same employer — never a match, only a question the
     * hand-typed add asks before founding a second person. Oldest first, as the dialog lists them.
     */
    List<Person> possibleDuplicates(UUID workspaceId, CandidateDetails details) {
        if (details.fullName() == null || details.fullName().isBlank() || details.employerName() == null) {
            return List.of();
        }
        return people.findNamedAtEmployer(workspaceId, details.fullName(), details.employerName()).stream()
                .sorted(OLDEST_FIRST)
                .toList();
    }

    /**
     * Whether saving {@code linkedinUrl} onto {@code person} may take that profile's key. An edit writes the
     * URL onto the workspace's person, so a mandate-scoped check cannot guard it: two people on one slug
     * would leave every later capture of it mapped to whichever is older.
     */
    ProfileClaim claimOf(UUID workspaceId, String linkedinUrl, Person person) {
        String slug = LinkedInUrls.profileSlugOrNull(linkedinUrl);
        boolean heldByAnother = slug != null && people.findByWorkspaceIdAndProfileSlug(workspaceId, slug)
                .filter(holder -> !holder.getId().equals(person.getId()))
                .isPresent();
        if (!heldByAnother) {
            return ProfileClaim.FREE;
        }
        return slug.equals(slugOf(person)) ? ProfileClaim.SHARED : ProfileClaim.HELD;
    }

    /**
     * Read off the URL rather than the stored key: V95 left the key null on a person who shares a profile
     * with an older one, and that person's URL still names the profile an address must not be crossed with.
     */
    private static String slugOf(Person person) {
        return LinkedInUrls.profileSlugOrNull(person.getLinkedinUrl());
    }
}
