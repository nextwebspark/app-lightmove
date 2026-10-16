package app.uncava.api.candidate.repository;

import app.uncava.api.candidate.model.PersonPhoto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * A person's stored photo. Reached only through a candidate the caller's mandate already proved it holds,
 * or a person the caller's workspace already proved it holds.
 */
public interface PersonPhotoRepository extends JpaRepository<PersonPhoto, UUID> {

    Optional<PersonPhoto> findByPersonId(UUID personId);

    boolean existsByPersonId(UUID personId);
}
