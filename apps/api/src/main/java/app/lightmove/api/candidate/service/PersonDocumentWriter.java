package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.PersonDocumentCategory;
import app.lightmove.api.candidate.constant.PersonDocumentUploadOutcome;
import app.lightmove.api.candidate.model.FiledPersonDocument;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonDocument;
import app.lightmove.api.candidate.model.PersonDocumentVersion;
import app.lightmove.api.candidate.model.StagedDocumentFile;
import app.lightmove.api.candidate.repository.PersonDocumentRepository;
import app.lightmove.api.candidate.repository.PersonDocumentVersionRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PersonDocumentSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.project.model.Project;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rows behind a person's documents, written with their timeline line in one transaction. Kept apart
 * from {@link PersonDocumentService} so the bytes move outside any transaction — a 20 MB upload must not
 * hold a pooled connection — and so each write here goes through the proxy rather than a self-call.
 */
@Component
class PersonDocumentWriter {

    private final PersonRepository people;
    private final PersonDocumentRepository documents;
    private final PersonDocumentVersionRepository versions;
    private final ProjectRepository projects;
    private final PersonActivityRecorder activity;
    private final PersonDocumentSettings settings;

    PersonDocumentWriter(PersonRepository people, PersonDocumentRepository documents,
                         PersonDocumentVersionRepository versions, ProjectRepository projects,
                         PersonActivityRecorder activity, LightMoveProperties properties) {
        this.people = people;
        this.documents = documents;
        this.versions = versions;
        this.projects = projects;
        this.activity = activity;
        this.settings = properties.personDocuments();
    }

    /**
     * A file under a name already on the person is that document's next version, unless the uploader
     * asked for a document of its own; anything else founds one. The first CV becomes the person's CV.
     */
    @Transactional
    public FiledPersonDocument file(UUID userId, UUID workspaceId, UUID personId, UUID projectId,
                                    StagedDocumentFile file, PersonDocumentCategory category,
                                    boolean asNewDocument) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        refuseDuplicate(personId, file);
        if (!asNewDocument) {
            List<PersonDocument> sameName = documents.findByWorkspaceIdAndPersonIdAndNameKey(workspaceId, personId,
                    PersonDocument.nameKeyOf(file.fileName()));
            if (!sameName.isEmpty()) {
                PersonDocument document = sameName.stream()
                        .max(Comparator.comparing(PersonDocument::getUpdatedAt)).orElseThrow();
                appendVersion(person, document, projectId, file, userId);
                return new FiledPersonDocument(PersonDocumentUploadOutcome.NEW_VERSION, document);
            }
        }
        if (documents.countByWorkspaceIdAndPersonId(workspaceId, personId) >= settings.maxDocumentsPerPerson()) {
            throw new ApiException(ErrorCode.PERSON_DOCUMENT_LIMIT,
                    "person " + personId + " already holds " + settings.maxDocumentsPerPerson() + " documents");
        }
        PersonDocumentCategory filedAs = category == null ? PersonDocumentCategory.guessFrom(file.fileName()) : category;
        PersonDocument document = PersonDocument.filed(person, filedAs, file.fileName(), projectId,
                titleOf(projectId), userId);
        if (filedAs == PersonDocumentCategory.CV
                && documents.findByWorkspaceIdAndPersonIdAndPrimaryCvTrue(workspaceId, personId).isEmpty()) {
            document.markPrimaryCv(true);
        }
        documents.save(document);
        versions.save(new PersonDocumentVersion(document, 1, file, userId));
        activity.record(person, projectId, userId, PersonActivityKind.DOCUMENT_ADDED,
                PersonActivityDetails.of("documentId", document.getId()).and("version", 1)
                        .and("category", document.getCategory()));
        return new FiledPersonDocument(PersonDocumentUploadOutcome.CREATED, document);
    }

    /** "Upload new version" on a card: the file joins that document whatever it is called. */
    @Transactional
    public FiledPersonDocument addVersion(UUID userId, UUID workspaceId, UUID personId, UUID documentId,
                                          UUID projectId, StagedDocumentFile file) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonDocument document = documents.requireOnPerson(documentId, workspaceId, personId);
        refuseDuplicate(personId, file);
        appendVersion(person, document, projectId, file, userId);
        return new FiledPersonDocument(PersonDocumentUploadOutcome.NEW_VERSION, document);
    }

    /** Taking the CV mark from another document first, so V105's one-per-person index never sees two. */
    @Transactional
    public PersonDocument update(UUID workspaceId, UUID personId, UUID documentId, String title,
                                 PersonDocumentCategory category, Boolean primaryCv) {
        PersonDocument document = documents.requireOnPerson(documentId, workspaceId, personId);
        if (title != null) {
            String stripped = title.strip();
            if (stripped.isEmpty()) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "title", "Give the document a name");
            }
            document.rename(stripped);
        }
        if (category != null) {
            document.recategorise(category);
        }
        if (Boolean.TRUE.equals(primaryCv)) {
            if (document.getCategory() != PersonDocumentCategory.CV) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "primaryCv",
                        "Only a CV can be the candidate's CV");
            }
            documents.findByWorkspaceIdAndPersonIdAndPrimaryCvTrue(workspaceId, personId)
                    .filter(current -> !current.getId().equals(documentId))
                    .ifPresent(current -> {
                        current.markPrimaryCv(false);
                        documents.saveAndFlush(current);
                    });
            document.markPrimaryCv(true);
        } else if (Boolean.FALSE.equals(primaryCv)) {
            document.markPrimaryCv(false);
        }
        return document;
    }

    /**
     * Whoever filed the document, or a workspace admin; answers the storage keys to delete once this has
     * committed, the rows going first.
     */
    @Transactional
    public List<String> remove(UUID userId, UUID workspaceId, UUID personId, UUID documentId, UUID projectId,
                               boolean mayRemoveAny) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonDocument document = documents.requireOnPerson(documentId, workspaceId, personId);
        if (!mayRemoveAny && !document.isFiledBy(userId)) {
            throw ApiException.of(ErrorCode.PERSON_DOCUMENT_NOT_YOURS);
        }
        return removeWhole(person, document, projectId, userId);
    }

    /**
     * Removing a document's only file removes the document, and the latest file going hands the name an
     * upload is matched on back to the one before it.
     */
    @Transactional
    public List<String> removeVersion(UUID userId, UUID workspaceId, UUID personId, UUID documentId,
                                      UUID versionId, UUID projectId, boolean mayRemoveAny) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        PersonDocument document = documents.requireOnPerson(documentId, workspaceId, personId);
        List<PersonDocumentVersion> stack = versions.findByDocumentIdOrderByVersionNoDesc(documentId);
        PersonDocumentVersion version = stack.stream().filter(file -> file.getId().equals(versionId)).findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!mayRemoveAny && !version.isUploadedBy(userId)) {
            throw ApiException.of(ErrorCode.PERSON_DOCUMENT_NOT_YOURS);
        }
        if (stack.size() == 1) {
            return removeWhole(person, document, projectId, userId);
        }
        activity.record(person, projectId, userId, PersonActivityKind.DOCUMENT_VERSION_REMOVED,
                PersonActivityDetails.of("documentId", documentId).and("version", version.getVersionNo())
                        .and("category", document.getCategory()));
        versions.delete(version);
        PersonDocumentVersion latest = stack.stream().filter(file -> !file.getId().equals(versionId)).findFirst()
                .orElseThrow();
        document.tookVersionNamed(latest.getFileName());
        return List.of(version.getStorageKey());
    }

    private List<String> removeWhole(Person person, PersonDocument document, UUID projectId, UUID userId) {
        List<PersonDocumentVersion> stack = versions.findByDocumentIdOrderByVersionNoDesc(document.getId());
        activity.record(person, projectId, userId, PersonActivityKind.DOCUMENT_REMOVED,
                PersonActivityDetails.of("documentId", document.getId()).and("category", document.getCategory())
                        .and("versions", stack.size()));
        boolean wasPrimaryCv = document.isPrimaryCv();
        versions.deleteAll(stack);
        documents.delete(document);
        documents.flush();
        if (wasPrimaryCv) {
            promoteNextCv(person);
        }
        return stack.stream().map(PersonDocumentVersion::getStorageKey).toList();
    }

    /** The person keeps a CV while they have one: the most recently touched takes the mark. */
    private void promoteNextCv(Person person) {
        documents.findByWorkspaceIdAndPersonId(person.getWorkspaceId(), person.getId()).stream()
                .filter(candidate -> candidate.getCategory() == PersonDocumentCategory.CV)
                .max(Comparator.comparing(PersonDocument::getUpdatedAt))
                .ifPresent(next -> next.markPrimaryCv(true));
    }

    private void appendVersion(Person person, PersonDocument document, UUID projectId, StagedDocumentFile file,
                               UUID userId) {
        if (versions.countByDocumentId(document.getId()) >= settings.maxVersionsPerDocument()) {
            throw new ApiException(ErrorCode.PERSON_DOCUMENT_LIMIT,
                    "document " + document.getId() + " already holds " + settings.maxVersionsPerDocument()
                            + " versions");
        }
        int next = versions.latestVersionNoOf(document.getId()) + 1;
        versions.save(new PersonDocumentVersion(document, next, file, userId));
        document.tookVersionNamed(file.fileName());
        activity.record(person, projectId, userId, PersonActivityKind.DOCUMENT_VERSION_ADDED,
                PersonActivityDetails.of("documentId", document.getId()).and("version", next)
                        .and("category", document.getCategory()));
    }

    /** Checked again here, though staging asked first: two tabs can send the same file at once. */
    private void refuseDuplicate(UUID personId, StagedDocumentFile file) {
        versions.findFirstByPersonIdAndSha256(personId, file.sha256()).ifPresent(existing -> {
            throw ApiException.withProperty(ErrorCode.PERSON_DOCUMENT_DUPLICATE, "duplicateOf",
                    Map.of("documentId", existing.getDocumentId(), "versionNo", existing.getVersionNo()));
        });
    }

    private String titleOf(UUID projectId) {
        return projectId == null ? null : projects.findById(projectId).map(Project::getPositionTitle).orElse(null);
    }
}
