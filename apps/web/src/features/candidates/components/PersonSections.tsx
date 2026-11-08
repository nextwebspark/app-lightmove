import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Button, LinesSkeleton, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { TaskNote } from "../../tasks/api/types";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { NOTE_MAX_STORED, NOTE_TOO_LONG, withoutMentionTokens } from "../../tasks/lib/mentions";
import { usePositionTeam } from "../../tasks/lib/usePositionTeam";
import { CONVERSATION_KEY } from "../../outreach/api/conversationApi";
import * as personCrmApi from "../api/personCrmApi";
import * as poolApi from "../api/poolApi";
import type { PersonNote, PersonNoteKind } from "../api/types";
import { isContactNote, notePlaceholderOf, PLAIN_NOTE_KIND } from "../lib/noteKinds";
import { useNoteCardActions } from "../lib/useNoteCardActions";
import type { Meeting } from "../../outreach/api/meetingApi";
import { isLogInFuture, LogDetailsFields, logDetailsNow, logPayloadOf, type LogDetails } from "./LogDetailsFields";
import { NoteCard, NoteKindPicker, NoteScopePicker, noteAnchorOf, type NoteCardLead, type NoteScope } from "./NoteParts";

/**
 * The shared person in a position's drawer — where else they are mapped, the notes on them, and what
 * has been done to them — each the body of one of the drawer's tabs. Staff-only: the drawer renders
 * none of these for a client seat, and none of them asks the server for anything until it is rendered.
 */

interface PersonSectionProps {
  projectId: string;
  candidateId: string;
}


export function usePositions(projectId: string, candidateId: string, enabled = true) {
  return useQuery({
    queryKey: personCrmApi.PERSON_POSITIONS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => personCrmApi.getPersonPositions(projectId, candidateId, signal),
    enabled,
  });
}

/**
 * The team's "do not contact", shown on every position the person sits on. Staff-only, like the pool
 * record it is read from: a client seat never reaches this drawer's staff sections.
 */
export function DoNotContactStrip({ personId }: { personId: string }) {
  const record = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(personId),
    queryFn: ({ signal }) => poolApi.getPerson(personId, signal),
  });
  const doNotContact = record.data?.doNotContact;
  if (!doNotContact) return null;
  return (
    <p
      role="note"
      className="mt-3 rounded-[8px] border border-u-offlimits/30 bg-u-offlimits-tint px-3 py-2 text-[12.5px] text-u-text2"
    >
      <b className="font-semibold text-u-offlimits">Do not contact.</b>
      {doNotContact.reason ? ` ${doNotContact.reason}` : ""}
    </p>
  );
}

/**
 * Restriction of processing, shown on every position the person sits on, as do not contact is: nothing uses them —
 * no AI, lookups or outreach — until someone lifts it from the Candidates drawer. Staff-only, read from the pool record.
 */
export function ProcessingRestrictedStrip({ personId }: { personId: string }) {
  const record = useQuery({
    queryKey: poolApi.PERSON_RECORD_KEY(personId),
    queryFn: ({ signal }) => poolApi.getPerson(personId, signal),
  });
  const restriction = record.data?.processingRestriction;
  if (!restriction) return null;
  return (
    <p role="note" className="mt-3 rounded-u-card border border-u-signal/35 bg-u-signal-tint px-3 py-2 text-note text-u-text2">
      <b className="font-semibold text-u-signal">Processing restricted.</b> {restriction.reason} No AI, lookups or
      outreach until it is lifted on the Candidates page.
    </p>
  );
}

/** A note to bring into view — the one a task was set from — stamped so asking again scrolls again. */
export interface NoteFocus {
  noteId: string;
  at: number;
}

export function NotesSection({
  projectId,
  candidateId,
  focus = null,
  onSetNextAction,
  onShowContactNote,
}: PersonSectionProps & {
  focus?: NoteFocus | null;
  /** A link to a call, LinkedIn or meeting note: those are Outreach's, so the drawer shows it there. */
  onShowContactNote?: (focus: NoteFocus) => void;
  /** Opens the Next action modal on the note just saved; without it the composer offers Save note alone. */
  onSetNextAction?: (note: TaskNote) => void;
}) {
  const notes = useQuery({
    queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
    queryFn: ({ signal }) => personCrmApi.getPersonNotes(projectId, candidateId, signal),
  });
  const allNotes = notes.data;
  const list = (allNotes ?? []).filter((note) => !isContactNote(note.kind));
  const loaded = notes.isSuccess;

  useEffect(() => {
    if (!focus || !loaded) return;
    const linked = allNotes?.find((note) => note.id === focus.noteId);
    if (linked && isContactNote(linked.kind) && onShowContactNote) {
      onShowContactNote(focus);
      return;
    }
    const card = document.getElementById(noteAnchorOf(focus.noteId));
    card?.scrollIntoView({ block: "center", behavior: "smooth" });
    card?.focus({ preventScroll: true });
  }, [focus, loaded, allNotes, onShowContactNote]);

  return (
    <section aria-label="Notes" className="py-4">
      <div className="flex flex-col gap-3">
        <PositionNoteComposer projectId={projectId} candidateId={candidateId} onSetNextAction={onSetNextAction} />

        {notes.isError ? (
          <p className="text-[13px] text-u-text3">{messageFor(notes.error)}</p>
        ) : notes.isPending ? (
          <LinesSkeleton />
        ) : list.length === 0 ? (
          <p className="text-[13px]/[1.6] text-u-text3">
            No notes yet. A note saved here stays with this position; choose All positions to share it with
            every position this person is in. Calls, LinkedIn messages and meeting notes are in Outreach.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {list.map((note) => (
              <PositionNoteCard
                key={note.id}
                projectId={projectId}
                candidateId={candidateId}
                note={note}
                flashAt={focus?.noteId === note.id ? focus.at : null}
              />
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

/**
 * A note on one executive, written from one position: its words, and whether it is about this position or all of
 * them. The drawer's Notes tab opens on a plain note; Log call, Log meeting and Log LinkedIn open it as a dialog on
 * their own kind (`kindChoices`).
 */
export function PositionNoteComposer({
  projectId,
  candidateId,
  initialKind = PLAIN_NOTE_KIND,
  kindChoices,
  pastMeetings = [],
  presetMeetingId = null,
  title = "New note",
  presentation = "inline",
  onExpandChange,
  onDraftChange,
  onSetNextAction,
  onSaved,
}: PersonSectionProps & {
  initialKind?: PersonNoteKind;
  /** The kinds the composer switches between; none drawn when it offers one. */
  kindChoices?: readonly PersonNoteKind[];
  /** A meeting log's Uncava meetings to choose from: this person's, already begun, newest first. */
  pastMeetings?: readonly Meeting[];
  /** The meeting a Log meeting notes on a call opens on. */
  presetMeetingId?: string | null;
  /** The larger view's title. */
  title?: string;
  /** In a dialog the composer is the dialog's whole body: no frame of its own, and room to write. */
  presentation?: "inline" | "dialog";
  /** In a dialog, which it fills: Expand grows the dialog, told through this. */
  onExpandChange?: (expanded: boolean) => void;
  /** Whether anything is written yet. */
  onDraftChange?: (hasDraft: boolean) => void;
  /** Opens the Next action modal on the note just saved; without it the composer offers Save note alone. */
  onSetNextAction?: (note: TaskNote) => void;
  onSaved?: () => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const team = usePositionTeam(projectId);
  const positions = usePositions(projectId, candidateId);
  const thisPosition = positions.data?.find((row) => row.projectId === projectId)?.positionTitle;
  const [kind, setKind] = useState<PersonNoteKind>(initialKind);
  const logs = kindChoices !== undefined && kindChoices.length > 0;
  const [details, setDetails] = useState<LogDetails>(() => logDetailsNow(presetMeetingId));
  const [scope, setScope] = useState<NoteScope>("position");
  const [draft, setDraft] = useState("");
  const forEveryPosition = scope === "everywhere";

  // A note about every position mentions nobody, so whoever was picked goes back to being a name.
  const changeScope = (next: NoteScope) => {
    setScope(next);
    if (next === "everywhere") setDraft(withoutMentionTokens(draft));
  };

  const body = draft.trim();
  const hasDraft = body.length > 0;
  useEffect(() => onDraftChange?.(hasDraft), [hasDraft, onDraftChange]);
  const tooLong = body.length > NOTE_MAX_STORED;
  const saving = useMutation({
    mutationFn: (_thenNextAction: boolean) =>
      personCrmApi.writePersonNote(
        projectId,
        candidateId,
        {
          kind,
          body,
          format: "markdown",
          ...(forEveryPosition ? { allPositions: true } : {}),
          ...(logs ? logPayloadOf(kind === "meeting" ? details : { ...details, meetingId: null }) : {}),
        },
      ),
    onSuccess: (note, thenNextAction) => {
      setDraft("");
      setDetails(logDetailsNow(presetMeetingId));
      void queryClient.invalidateQueries({
        queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
      });
      void queryClient.invalidateQueries({
        queryKey: personCrmApi.PERSON_TIMELINE_KEY(projectId, candidateId),
      });
      void queryClient.invalidateQueries({ queryKey: CONVERSATION_KEY(projectId, candidateId) });
      if (thenNextAction && onSetNextAction) {
        toast("Note saved");
        onSetNextAction(taskNoteOf(note));
      } else {
        toast(note.projectId ? "Note saved" : "Note saved — your team sees it on every position");
      }
      onSaved?.();
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const canSave = body.length > 0 && !tooLong && !saving.isPending && !(logs && isLogInFuture(details, kind));
  const save = () => canSave && saving.mutate(false);

  const isDialog = presentation === "dialog";
  return (
    <div className={cn(isDialog ? "flex min-h-0 flex-1 flex-col" : "rounded-[8px] border border-u-border p-2.5")}>
      <NoteEditor
        value={draft}
        format="markdown"
        onChange={setDraft}
        teammates={forEveryPosition ? null : team.teammates}
        ariaLabel="New note"
        placeholder={kindChoices && kindChoices.length > 1 ? "What was said, and what happens next…" : notePlaceholderOf(kind)}
        onSubmit={save}
        submitLabel="Save note"
        canSubmit={canSave}
        expandTitle={title}
        roomy={isDialog}
        autoFocus={isDialog}
        fillsDialog={isDialog}
        onExpandChange={onExpandChange}
        header={
          logs ? (
            <>
              {kindChoices.length > 1 && (
                <NoteKindPicker value={kind} onChange={setKind} kinds={kindChoices} label="What you are logging" />
              )}
              <LogDetailsFields kind={kind} pastMeetings={pastMeetings} value={details} onChange={setDetails} />
            </>
          ) : undefined
        }
        footer={
          <>
            {tooLong && (
              <p role="alert" className="mb-2 text-meta text-u-offlimits">
                {NOTE_TOO_LONG}
              </p>
            )}
            <div className="flex flex-wrap items-center gap-2">
              <div className="min-w-0 flex-1">
                <NoteScopePicker positionTitle={thisPosition} value={scope} onChange={changeScope} />
              </div>
              {onSetNextAction && (
                <Button
                  type="button"
                  size={isDialog ? "sm" : "xs"}
                  variant="secondary"
                  disabled={!canSave}
                  loading={saving.isPending && saving.variables}
                  onClick={() => saving.mutate(true)}
                >
                  Save & set next action
                </Button>
              )}
              <Button
                type="button"
                size={isDialog ? "sm" : "xs"}
                disabled={!canSave}
                loading={saving.isPending && !saving.variables}
                onClick={() => saving.mutate(false)}
              >
                Save note
              </Button>
            </div>
          </>
        }
      />
    </div>
  );
}

export function PositionNoteCard({
  projectId,
  candidateId,
  note,
  flashAt,
  showsKindMark,
  lead = null,
}: PersonSectionProps & {
  note: PersonNote;
  flashAt: number | null;
  showsKindMark?: boolean;
  lead?: NoteCardLead | null;
}) {
  const queryClient = useQueryClient();
  const actions = useNoteCardActions(
    note,
    {
      pin: (noteId, pinned) => personCrmApi.pinPersonNote(projectId, candidateId, noteId, pinned),
      revise: (current, body) =>
        personCrmApi.revisePersonNote(projectId, candidateId, current.id, { kind: current.kind, body, format: "markdown" }),
      remove: (noteId) => personCrmApi.removePersonNote(projectId, candidateId, noteId),
      restore: (noteId) => personCrmApi.restorePersonNote(projectId, candidateId, noteId),
    },
    () => {
      void queryClient.invalidateQueries({ queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId) });
      void queryClient.invalidateQueries({ queryKey: personCrmApi.PERSON_TIMELINE_KEY(projectId, candidateId) });
      void queryClient.invalidateQueries({ queryKey: CONVERSATION_KEY(projectId, candidateId) });
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "notes"] });
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "timeline"] });
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "activity"] });
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "page"] });
    },
  );
  const team = usePositionTeam(note.projectId);
  return (
    <NoteCard
      note={note}
      actions={actions}
      teammates={note.projectId ? team.teammates : null}
      flashAt={flashAt}
      showsKindMark={showsKindMark}
      lead={lead}
      namesPosition={false}
    />
  );
}

/** The note a task is set from, as the task will name it: by kind and date. */
export function taskNoteOf(note: PersonNote): TaskNote {
  return { id: note.id, kind: note.kind, writtenAt: note.createdAt };
}

export function TabSectionHeading({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="mb-2.5 flex items-center gap-2">
      <h3 className="text-[13px] font-semibold">{title}</h3>
      {action && <span className="ms-auto">{action}</span>}
    </div>
  );
}
