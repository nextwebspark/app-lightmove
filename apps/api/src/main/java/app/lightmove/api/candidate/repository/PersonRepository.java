package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.Person;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The workspace's people. Every finder carries the workspace id, taken from the caller's principal one
 * layer up: a person is tenant data, and a lookup that could answer across firms must not exist.
 */
public interface PersonRepository extends JpaRepository<Person, UUID> {

    /** The person the workspace holds a LinkedIn profile as — one at most, by V92's unique index. */
    Optional<Person> findByWorkspaceIdAndProfileSlug(UUID workspaceId, String profileSlug);

    /** The people holding one address, on the key the ledger dedupes by. */
    @Query("""
            select distinct p from Person p join p.contacts k
            where p.workspaceId = :workspaceId and k.channel = 'EMAIL' and k.valueKey = :emailKey
            """)
    List<Person> findByWorkspaceIdAndEmailKey(UUID workspaceId, String emailKey);
}
