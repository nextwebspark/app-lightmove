package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateMaterial;
import app.lightmove.api.candidate.model.DocumentOnFile;
import app.lightmove.api.candidate.model.NoteOnFile;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonDocumentVersion;
import app.lightmove.api.candidate.model.StoredDocumentFile;
import app.lightmove.api.candidate.repository.CandidateRepository;
import app.lightmove.api.candidate.repository.PersonDocumentRepository;
import app.lightmove.api.candidate.repository.PersonDocumentVersionRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import java.io.InputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the AI runs on an executive read beyond the profile: the person's notes and documents, which are the
 * workspace's and shared by every mandate mapping them. Reading here audits nothing; the run that asked does.
 */
@Service
@RequiredArgsConstructor
public class CandidateMaterialService {

    /** The primary CV first, then a CV, then the rest by kind, each kind newest first. */
    private static final Comparator<DocumentOnFile> READING_ORDER = Comparator
            .comparing(DocumentOnFile::primaryCv).reversed()
            .thenComparing(DocumentOnFile::category)
            .thenComparing(DocumentOnFile::uploadedAt, Comparator.reverseOrder());

    private final CandidateRepository candidates;
    private final PersonNoteRepository notes;
    private final PersonDocumentRepository documents;
    private final PersonDocumentVersionRepository versions;
    private final PersonDocumentService documentFiles;

    /** Nothing when the executive is gone. */
    @Transactional(readOnly = true)
    public CandidateMaterial materialOf(UUID projectId, UUID candidateId) {
        return candidates.findByIdAndProjectId(candidateId, projectId)
                .map(Candidate::getPerson)
                .map(this::materialOf)
                .orElse(CandidateMaterial.NONE);
    }

    public InputStream open(StoredDocumentFile file) {
        return documentFiles.open(file);
    }

    private CandidateMaterial materialOf(Person person) {
        List<NoteOnFile> written = notes
                .findByWorkspaceIdAndPersonIdOrderByCreatedAtDesc(person.getWorkspaceId(), person.getId())
                .stream()
                .map(note -> new NoteOnFile(note.getKind(), note.getCreatedAt(), note.getProjectTitle(),
                        note.getBody()))
                .toList();
        Map<UUID, PersonDocumentVersion> latest = new LinkedHashMap<>();
        versions.findByPersonIdOrderByVersionNoDesc(person.getId())
                .forEach(version -> latest.putIfAbsent(version.getDocumentId(), version));
        List<DocumentOnFile> filed = documents.findByWorkspaceIdAndPersonId(person.getWorkspaceId(), person.getId())
                .stream()
                .filter(document -> latest.containsKey(document.getId()))
                .map(document -> {
                    PersonDocumentVersion version = latest.get(document.getId());
                    return new DocumentOnFile(document.getTitle(), document.getCategory(), document.isPrimaryCv(),
                            version.getUploadedAt(), version.toStoredFile(false));
                })
                .sorted(READING_ORDER)
                .toList();
        return new CandidateMaterial(written, filed);
    }
}
