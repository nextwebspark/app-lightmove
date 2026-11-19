package app.lightmove.api.candidate.model;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** When something last happened about one person, without what it was. */
public interface PersonLatestAtRow {

    UUID getPersonId();

    Instant getAt();

    static Map<UUID, Instant> byPerson(Collection<? extends PersonLatestAtRow> rows) {
        return rows.stream().collect(Collectors.toMap(PersonLatestAtRow::getPersonId, PersonLatestAtRow::getAt));
    }
}
