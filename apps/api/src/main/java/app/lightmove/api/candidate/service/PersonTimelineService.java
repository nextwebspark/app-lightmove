package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.constant.TimelineGroup;
import app.lightmove.api.candidate.dto.FieldChangeResponse;
import app.lightmove.api.candidate.dto.PersonTimelineEntryResponse;
import app.lightmove.api.candidate.dto.PersonTimelineResponse;
import app.lightmove.api.candidate.model.CandidateTag;
import app.lightmove.api.candidate.model.NoteViewpoint;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonActivity;
import app.lightmove.api.candidate.model.PersonDocument;
import app.lightmove.api.candidate.model.PersonNote;
import app.lightmove.api.candidate.repository.CandidateTagRepository;
import app.lightmove.api.candidate.repository.PersonActivityRepository;
import app.lightmove.api.candidate.repository.PersonDocumentRepository;
import app.lightmove.api.candidate.repository.PersonNoteRepository;
import app.lightmove.api.candidate.repository.PersonRepository;
import app.lightmove.api.common.note.NoteText;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads {@code app_lm_person_activity} back: one person's history, or the workspace's feed. Staff-only
 * like the notes, and an allowlist like {@code ProjectActivityService}'s — a detail key nobody listed
 * here never reaches a screen, whatever a writer later puts in it.
 */
@Service
@RequiredArgsConstructor
public class PersonTimelineService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    /** A position's drawer reads its whole history at once to count and filter it, so its own read pages larger. */
    public static final int POSITION_MAX_PAGE_SIZE = 500;
    private static final int EXCERPT_LENGTH = 140;
    private static final Set<String> DETAIL_KEYS = Set.of("door", "backgroundConfirmed", "from", "to", "vendor",
            "runId", "emails", "phones", "channel", "found", "via", "noteId", "kind", "tagId", "tag",
            "ownerUserId", "owner", "documentId", "version", "category", "versions", "fromLabel", "toLabel",
            "text", "dueAt", "cleared", "visible", "taskId", "title", "taskKind", "assignee", "previousAssignee",
            "rule", "sequenceId", "sequence", "step", "reason", "startsAt", "viaLink", "manual", "withCompany",
            "fields", "call", "callAt", "recordingId", "reverted", "scope", "previousStartsAt", "messageId",
            "automation");
    /**
     * Changes to the person rather than to a position's work, read on every position they are on: a position's
     * drawer reads what it shows (the profile, the contacts, the documents), so who changed it is that position's
     * business too. Anything else made on another position (its notes, emails, meetings, status, tasks) is not.
     */
    static final Set<PersonActivityKind> PERSON_LEVEL_KINDS = EnumSet.of(PersonActivityKind.PROFILE_EDITED,
            PersonActivityKind.CONTACTS_EDITED, PersonActivityKind.CONTACT_FOUND, PersonActivityKind.RESEARCHED,
            PersonActivityKind.TAGGED, PersonActivityKind.UNTAGGED, PersonActivityKind.OWNER_CHANGED,
            PersonActivityKind.DO_NOT_CONTACT_SET, PersonActivityKind.DO_NOT_CONTACT_CLEARED,
            PersonActivityKind.DOCUMENT_ADDED, PersonActivityKind.DOCUMENT_VERSION_ADDED,
            PersonActivityKind.DOCUMENT_REMOVED, PersonActivityKind.DOCUMENT_VERSION_REMOVED);
    private static final Set<PersonActivityKind> NOTE_KINDS = TimelineGroup.NOTES.kinds();
    private static final List<String> RETIRED_KIND_NAMES = PersonActivityKind.RETIRED.stream().map(Enum::name)
            .toList();
    private static final Set<PersonActivityKind> DOCUMENT_KINDS = TimelineGroup.DOCUMENTS.kinds();
    /** Bound when a filter is open, so the parameter is never an untyped null; matches no row. */
    private static final UUID NOBODY = new UUID(0, 0);
    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");

    private final PersonRepository people;
    private final PersonActivityRepository activity;
    private final PersonNoteRepository notes;
    private final UserRepository users;
    private final CandidateTagRepository tags;
    private final PersonDocumentRepository documents;

    /** One person's whole history across every position, as the workspace's Candidates page reads it. */
    @Transactional(readOnly = true)
    public PersonTimelineResponse workspaceTimelineOf(UUID workspaceId, UUID personId, String group, Long before,
                                                      Integer limit) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        int size = sizeOf(limit, MAX_PAGE_SIZE);
        List<PersonActivity> page = activity.findPersonTimeline(workspaceId, personId, cursorOf(before),
                TimelineGroup.kindsOf(group), Limit.of(size + 1));
        return pageOf(workspaceId, page, size, Map.of(person.getId(), person), NoteViewpoint.workspace(),
                UnaryOperator.identity());
    }

    /**
     * One person's history as one position reads it: its own lines and the person-level ones, newest first. The
     * boundary is the query's, so another position's work never leaves the database; a person-level line made on
     * another position is answered without naming it.
     */
    @Transactional(readOnly = true)
    public PersonTimelineResponse positionTimelineOf(UUID workspaceId, UUID personId, UUID projectId, String group,
                                                     Long before, Integer limit) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        int size = sizeOf(limit, POSITION_MAX_PAGE_SIZE);
        List<PersonActivity> page = activity.findPositionScopedTimeline(workspaceId, personId, projectId,
                cursorOf(before), TimelineGroup.kindsOf(group), PERSON_LEVEL_KINDS, Limit.of(size + 1));
        return pageOf(workspaceId, page, size, Map.of(person.getId(), person), NoteViewpoint.position(projectId),
                entry -> entry.projectId() == null || entry.projectId().equals(projectId) ? entry
                        : entry.withoutPosition());
    }

    /** The workspace's feed; each filter left null matches everything. */
    @Transactional(readOnly = true)
    public PersonTimelineResponse feedOf(UUID workspaceId, UUID actor, UUID projectId, String group,
                                         Instant from, Instant to, Long before, Integer limit) {
        int size = sizeOf(limit, MAX_PAGE_SIZE);
        List<PersonActivity> page = activity.findWorkspaceFeed(workspaceId, cursorOf(before),
                TimelineGroup.kindsOf(group),
                actor == null, actor == null ? NOBODY : actor,
                projectId == null, projectId == null ? NOBODY : projectId,
                from == null ? Instant.EPOCH : from, to == null ? FAR_FUTURE : to,
                Limit.of(size + 1));
        List<UUID> personIds = page.stream().map(PersonActivity::getPersonId).distinct().toList();
        Map<UUID, Person> persons = people.findByWorkspaceIdAndIdIn(workspaceId, personIds).stream()
                .collect(Collectors.toMap(Person::getId, Function.identity()));
        return pageOf(workspaceId, page, size, persons, NoteViewpoint.workspace(), UnaryOperator.identity());
    }

    /**
     * One person's lines about one position, newest first, held to {@code kinds} — the outreach conversation's
     * run and status events. Unpaged: a person's lines on one position are tens, and {@code limit} bounds them.
     */
    @Transactional(readOnly = true)
    public List<PersonTimelineEntryResponse> positionLinesOf(UUID workspaceId, UUID personId, UUID projectId,
                                                             Set<PersonActivityKind> kinds, int limit) {
        Person person = people.requireInWorkspace(personId, workspaceId);
        List<PersonActivity> lines = activity.findPositionTimeline(workspaceId, personId, projectId, kinds,
                Limit.of(limit));
        return entriesOf(workspaceId, lines, Map.of(person.getId(), person), NoteViewpoint.position(projectId));
    }

    /** Each person's latest line, for the Candidates page's Last activity column. */
    @Transactional(readOnly = true)
    public Map<UUID, PersonTimelineEntryResponse> latestOf(UUID workspaceId, Collection<Person> persons) {
        if (persons.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Person> byId = persons.stream().collect(Collectors.toMap(Person::getId, Function.identity()));
        List<PersonActivity> latest = activity.findLatestOfPeople(workspaceId, byId.keySet(), RETIRED_KIND_NAMES);
        return entriesOf(workspaceId, latest, byId, NoteViewpoint.workspace()).stream()
                .collect(Collectors.toMap(PersonTimelineEntryResponse::personId, Function.identity()));
    }

    private PersonTimelineResponse pageOf(UUID workspaceId, List<PersonActivity> page, int size,
                                          Map<UUID, Person> persons, NoteViewpoint viewpoint,
                                          UnaryOperator<PersonTimelineEntryResponse> asShown) {
        boolean hasMore = page.size() > size;
        List<PersonActivity> shown = hasMore ? page.subList(0, size) : page;
        return new PersonTimelineResponse(entriesOf(workspaceId, shown, persons, viewpoint).stream()
                .map(asShown).toList(), hasMore ? shown.getLast().getId() : null);
    }

    private List<PersonTimelineEntryResponse> entriesOf(UUID workspaceId, List<PersonActivity> shown,
                                                        Map<UUID, Person> persons, NoteViewpoint viewpoint) {
        Map<UUID, PersonNote> liveNotes = notes.findReadableIn(workspaceId, shown.stream()
                        .map(PersonTimelineService::noteIdOf).filter(Objects::nonNull).distinct().toList(), viewpoint)
                .stream().collect(Collectors.toMap(PersonNote::getId, Function.identity()));
        Map<UUID, User> named = users.findAllById(Stream.concat(
                        shown.stream().flatMap(line -> Stream.of(line.getActorUserId(), idIn(line, "ownerUserId"))),
                        liveNotes.values().stream().map(PersonNote::getEditedBy))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, CandidateTag> liveTags = tags.findByWorkspaceIdAndIdIn(workspaceId, shown.stream()
                        .map(line -> idIn(line, "tagId")).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(CandidateTag::getId, Function.identity()));
        Map<UUID, PersonDocument> liveDocuments = documents.findByWorkspaceIdAndIdIn(workspaceId, shown.stream()
                        .map(PersonTimelineService::documentIdOf).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(PersonDocument::getId, Function.identity()));

        return shown.stream().map(line -> {
            User actor = line.getActorUserId() == null ? null : named.get(line.getActorUserId());
            Person person = persons.get(line.getPersonId());
            UUID noteId = noteIdOf(line);
            PersonNote note = noteId == null ? null : liveNotes.get(noteId);
            User noteEditor = note == null || note.getEditedBy() == null ? null : named.get(note.getEditedBy());
            return new PersonTimelineEntryResponse(line.getId(), line.getKind().name(), line.getOccurredAt(),
                    line.getActorUserId(), actor == null ? null : actor.getFullName(),
                    actor == null ? null : actor.getAvatarUrl(),
                    line.getPersonId(), person == null ? null : person.getFullName(),
                    line.getProjectId(), line.getProjectTitle(),
                    detailsOf(line, named, liveTags, liveDocuments, note),
                    note == null ? null : excerptOf(note), note == null ? null : note.getEditedAt(),
                    noteEditor == null ? null : noteEditor.getFullName(), changesOf(line));
        }).toList();
    }

    /**
     * A tag reads as it is spelled today, an owner by their name today, a document by its title while
     * it exists and a note by its kind today; the line keeps only the ids, so a removed document leaves no
     * name behind.
     */
    private static Map<String, String> detailsOf(PersonActivity line, Map<UUID, User> named,
                                                 Map<UUID, CandidateTag> liveTags,
                                                 Map<UUID, PersonDocument> liveDocuments, PersonNote liveNote) {
        Map<String, String> shown = new LinkedHashMap<>();
        line.getDetails().forEach((key, value) -> {
            if (DETAIL_KEYS.contains(key) && value != null) {
                shown.put(key, String.valueOf(value));
            }
        });
        UUID tagId = idIn(line, "tagId");
        if (tagId != null && liveTags.containsKey(tagId)) {
            shown.put("tag", liveTags.get(tagId).getLabel());
        }
        UUID ownerId = idIn(line, "ownerUserId");
        if (ownerId != null && named.containsKey(ownerId)) {
            shown.put("owner", named.get(ownerId).getFullName());
        }
        UUID documentId = documentIdOf(line);
        if (documentId != null && liveDocuments.containsKey(documentId)) {
            shown.put("document", liveDocuments.get(documentId).getTitle());
        }
        if (liveNote != null) {
            shown.put("kind", liveNote.getKind().name());
        }
        return shown;
    }

    /** A line written before changes were recorded, or one whose stored shape is not a list of changes, has none. */
    private static List<FieldChangeResponse> changesOf(PersonActivity line) {
        if (!(line.getDetails().get(ProfileChanges.DETAIL_KEY) instanceof List<?> stored)) {
            return List.of();
        }
        return stored.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(change -> change.get("field") instanceof String)
                .map(change -> new FieldChangeResponse((String) change.get("field"), textOf(change.get("from")),
                        textOf(change.get("to"))))
                .toList();
    }

    private static String textOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static UUID noteIdOf(PersonActivity line) {
        return NOTE_KINDS.contains(line.getKind()) ? idIn(line, "noteId") : null;
    }

    private static UUID documentIdOf(PersonActivity line) {
        return DOCUMENT_KINDS.contains(line.getKind()) ? idIn(line, "documentId") : null;
    }

    private static UUID idIn(PersonActivity line, String key) {
        Object id = line.getDetails().get(key);
        try {
            return id == null ? null : UUID.fromString(id.toString());
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private static String excerptOf(PersonNote note) {
        String flat = NoteText.plainTextOf(note.getBody(), note.getBodyFormat()).strip().replaceAll("\\s+", " ");
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH - 1).strip() + "…";
    }

    private static int sizeOf(Integer limit, int most) {
        return limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, most);
    }

    private static long cursorOf(Long before) {
        return before == null ? Long.MAX_VALUE : before;
    }
}
