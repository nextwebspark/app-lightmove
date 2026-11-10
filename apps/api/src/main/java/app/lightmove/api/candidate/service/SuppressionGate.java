package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.SuppressionKey;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What every door asks the workspace's suppression list (#920): the add form, the plugin and a spreadsheet are
 * refused with {@code PERSON_SUPPRESSED}, while Find executives and People Search leave a suppressed person out
 * before anything is filed or fetched. Keys are normalised the way {@link PersonMatcher} reads a person.
 */
@Service
@RequiredArgsConstructor
public class SuppressionGate {

    private final SuppressionEntries entries;

    /** Refuses a filing or an edit carrying a suppressed profile or address. */
    @Transactional(readOnly = true)
    public void refuseSuppressed(UUID workspaceId, String linkedinUrl, Collection<String> emails) {
        List<SuppressionKey> keys = new ArrayList<>();
        SuppressionKey profile = SuppressionKey.ofProfileUrlOrNull(linkedinUrl);
        if (profile != null) {
            keys.add(profile);
        }
        emails.stream().map(SuppressionKey::ofEmailOrNull).filter(Objects::nonNull).distinct().forEach(keys::add);
        if (!entries.entriesFor(workspaceId, keys).isEmpty()) {
            throw ApiException.of(ErrorCode.PERSON_SUPPRESSED);
        }
    }

    /**
     * Refuses mapping someone the workspace already knows whose own profile or any address they hold is listed.
     * A door's request may carry none of them — a name answered with an existing person, a capture matched by
     * profile to someone suppressed by email — so the person's keys are asked, not the request's.
     */
    @Transactional(readOnly = true)
    public void refuseSuppressedPerson(UUID workspaceId, Person person) {
        if (!suppressedPeopleOf(workspaceId, List.of(person)).isEmpty()) {
            throw ApiException.of(ErrorCode.PERSON_SUPPRESSED);
        }
    }

    /** The ids of those people whose own profile or any address they hold is listed — one query for any number. */
    @Transactional(readOnly = true)
    public Set<UUID> suppressedPeopleOf(UUID workspaceId, Collection<Person> people) {
        Map<SuppressionKey, Set<UUID>> peopleByKey = new HashMap<>();
        for (Person person : people) {
            SuppressionKey profile = SuppressionKey.ofProfileUrlOrNull(person.getLinkedinUrl());
            if (profile != null) {
                peopleByKey.computeIfAbsent(profile, key -> new HashSet<>()).add(person.getId());
            }
            for (CandidateContact email : person.emailContacts()) {
                SuppressionKey key = SuppressionKey.ofEmailOrNull(email.getValue());
                if (key != null) {
                    peopleByKey.computeIfAbsent(key, held -> new HashSet<>()).add(person.getId());
                }
            }
        }
        Set<UUID> suppressed = new HashSet<>();
        entries.entriesFor(workspaceId, peopleByKey.keySet()).keySet()
                .forEach(key -> suppressed.addAll(peopleByKey.get(key)));
        return suppressed;
    }

    /** Which of these profile slugs the list holds, in every spelling they were given — one query for a page. */
    @Transactional(readOnly = true)
    public Set<String> suppressedProfileSlugsOf(UUID workspaceId, Collection<String> slugs) {
        Map<SuppressionKey, Set<String>> spellingsByKey = slugs.stream()
                .filter(slug -> slug != null && !slug.isBlank())
                .collect(Collectors.groupingBy(SuppressionKey::ofProfileSlug, Collectors.toSet()));
        Set<String> suppressed = new HashSet<>();
        entries.entriesFor(workspaceId, spellingsByKey.keySet()).keySet()
                .forEach(key -> suppressed.addAll(spellingsByKey.get(key)));
        return suppressed;
    }
}
