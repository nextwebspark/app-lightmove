package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.PersonDocumentVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The files of a person's documents. Reached only through a {@code PersonDocument} the caller has
 * already scoped to a workspace, or by a person id that has been.
 */
public interface PersonDocumentVersionRepository extends JpaRepository<PersonDocumentVersion, UUID> {

    List<PersonDocumentVersion> findByPersonIdOrderByVersionNoDesc(UUID personId);

    List<PersonDocumentVersion> findByDocumentIdOrderByVersionNoDesc(UUID documentId);

    Optional<PersonDocumentVersion> findByIdAndDocumentId(UUID id, UUID documentId);

    /** The same bytes already on this person, under any document. */
    Optional<PersonDocumentVersion> findFirstByPersonIdAndSha256(UUID personId, String sha256);

    long countByDocumentId(UUID documentId);

    @Query("select coalesce(max(v.versionNo), 0) from PersonDocumentVersion v where v.documentId = :documentId")
    int latestVersionNoOf(UUID documentId);
}
