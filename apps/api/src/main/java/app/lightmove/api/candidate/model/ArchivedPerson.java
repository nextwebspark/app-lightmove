package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** A person as a whole-workspace export writes them: their notes and the latest file of each document. */
@NamedInterface(name = "api", propagate = false)
public record ArchivedPerson(UUID personId, String fullName, List<ArchivedNote> notes,
                             List<ArchivedDocumentFile> documents) {
}
