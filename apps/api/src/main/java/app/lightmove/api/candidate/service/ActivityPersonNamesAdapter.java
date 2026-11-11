package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.project.service.ActivityPersonNames;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Answers {@link ActivityPersonNames} for {@code project}, which owns it. */
@Component
@RequiredArgsConstructor
class ActivityPersonNamesAdapter implements ActivityPersonNames {

    private final PersonRepository people;

    @Override
    public Map<UUID, String> namesOf(UUID workspaceId, Collection<UUID> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return people.findByWorkspaceIdAndIdIn(workspaceId, personIds).stream()
                .filter(person -> person.getFullName() != null)
                .collect(Collectors.toMap(Person::getId, Person::getFullName));
    }
}
