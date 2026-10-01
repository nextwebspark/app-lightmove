package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.PersonPhoto;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** A person's stored photo. Reached only through a candidate the caller's mandate already proved it holds. */
public interface PersonPhotoRepository extends JpaRepository<PersonPhoto, UUID> {

    Optional<PersonPhoto> findByPersonId(UUID personId);

    boolean existsByPersonId(UUID personId);
}
