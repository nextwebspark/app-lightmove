import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Button, LinesSkeleton, Spinner, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatActivityTime } from "../../projects/lib/activity";
import type { TaskNote } from "../../tasks/api/types";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { NOTE_MAX_STORED, NOTE_TOO_LONG, withoutMentionTokens } from "../../tasks/lib/mentions";
import { usePositionTeam } from "../../tasks/lib/usePositionTeam";
import * as personCrmApi from "../api/personCrmApi";
import * as poolApi from "../api/poolApi";
import type { PersonNote, PersonNoteKind } from "../api/types";
import { timelineLines } from "../lib/candidateActivity";
import { useNoteCardActions } from "../lib/useNoteCardActions";
import { NoteCard, NoteKindPicker, NoteScopePicker, noteAnchorOf, type NoteScope } from "./NoteParts";

/**
 * The shared person in a position's drawer — where else they are mapped, the notes on them, and what
 * has been done to them — each the body of one of the drawer's tabs. Staff-only: the drawer renders
 * none of these for a client seat, and none of them asks the server for anything until it is rendered.
 */

interface PersonSectionProps {
  projectId: string;
  candidateId: string;
}

const TIMELINE_PREVIEW = 5;

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
}: PersonSectionProps & {
  focus?: NoteFocus | null;
  /** Opens the Next action modal on the note just saved; without it the composer offers Save note alone. */
  onSetNextAction?: (note: TaskNote) => void;
}) {
  const notes = useQuery({
    queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
    queryFn: ({ signal }) => personCrmApi.getPersonNotes(projectId, candidateId, signal),
  });
  const list = notes.data ?? [];
  const loaded = notes.isSuccess;

  useEffect(() => {
    if (!focus || !loaded) return;
    const card = document.getElementById(noteAnchorOf(focus.noteId));
    card?.scrollIntoView({ block: "center", behavior: "smooth" });
    card?.focus({ preventScroll: true });
  }, [focus, loaded]);

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
            every position this person is in.
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
 * A note on one executive, written from one position: its kind, its words, and whether it is about this position or
 * all of them. The drawer's Notes tab opens on it, and the Outreach page's Log call and Add note open it as a dialog.
 */
export function PositionNoteComposer({
  projectId,
  candidateId,
  initialKind = "general",
  title = "New note",
  presentation = "inline",
  onSetNextAction,
  onSaved,
}: PersonSectionProps & {
  initialKind?: PersonNoteKind;
  /** The larger view's title. */
  title?: string;
  /** In a dialog the composer is the dialog's whole body: no frame of its own, and room to write. */
  presentation?: "inline" | "dialog";
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
  const [scope, setScope] = useState<NoteScope>("position");
  const [draft, setDraft] = useState("");
  const forEveryPosition = scope === "everywhere";

  // A note about every position mentions nobody, so whoever was picked goes back to being a name.
  const changeScope = (next: NoteScope) => {
    setScope(next);
    if (next === "everywhere") setDraft(withoutMentionTokens(draft));
  };

  const body = draft.trim();
  const tooLong = body.length > NOTE_MAX_STORED;
  const saving = useMutation({
    mutationFn: (_thenNextAction: boolean) =>
      personCrmApi.writePersonNote(
        projectId,
        candidateId,
        forEveryPosition
          ? { kind, body, format: "markdown", allPositions: true }
          : { kind, body, format: "markdown" },
      ),
    onSuccess: (note, thenNextAction) => {
      setDraft("");
      void queryClient.invalidateQueries({
        queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
      });
      void queryClient.invalidateQueries({
        queryKey: personCrmApi.PERSON_TIMELINE_KEY(projectId, candidateId),
      });
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
  const canSave = body.length > 0 && !tooLong && !saving.isPending;
  const save = () => canSave && saving.mutate(false);

  const isDialog = presentation === "dialog";
  return (
    <div className={cn(!isDialog && "rounded-[8px] border border-u-border p-2.5")}>
      <NoteEditor
        value={draft}
        format="markdown"
        onChange={setDraft}
        teammates={forEveryPosition ? null : team.teammates}
        ariaLabel="New note"
        placeholder="What was said, and what happens next…"
        onSubmit={save}
        submitLabel="Save note"
        canSubmit={canSave}
        expandTitle={title}
        roomy={isDialog}
        autoFocus={isDialog}
        header={<NoteKindPicker value={kind} onChange={setKind} />}
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

function PositionNoteCard({
  projectId,
  candidateId,
  note,
  flashAt,
}: PersonSectionProps & { note: PersonNote; flashAt: number | null }) {
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
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "notes"] });
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "timeline"] });
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "activity"] });
      void queryClient.invalidateQueries({ queryKey: [...poolApi.POOL_KEY, "page"] });
    },
  );
  const team = usePositionTeam(note.projectId);
  return (
    <NoteCard note={note} actions={actions} teammates={note.projectId ? team.teammates : null} flashAt={flashAt} />
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

/** A long history cut to its first lines, and the button that shows the rest of what is loaded. */
export function useTimelineCut<T>(lines: readonly T[], shown = TIMELINE_PREVIEW) {
  const [isExpanded, setIsExpanded] = useState(false);
  const isClipped = !isExpanded && lines.length > shown;
  return {
    visible: isClipped ? lines.slice(0, shown) : lines,
    isClipped,
    seeMore: isClipped ? (
      <button
        type="button"
        onClick={() => setIsExpanded(true)}
        className="mt-3 text-note font-medium text-u-accent hover:underline"
      >
        See more
      </button>
    ) : null,
  };
}

export function TimelineFeed({ projectId, candidateId }: PersonSectionProps) {
  const timeline = useInfiniteQuery({
    queryKey: personCrmApi.PERSON_TIMELINE_KEY(projectId, candidateId),
    queryFn: ({ pageParam, signal }) =>
      personCrmApi.getPersonTimeline(projectId, candidateId, pageParam, signal),
    initialPageParam: null as number | null,
    getNextPageParam: (last) => last.nextCursor,
  });
  const lines = timelineLines(
    timeline.data?.pages.flatMap((page) => page.entries) ?? [],
    projectId,
  );
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = timeline;
  const cut = useTimelineCut(lines);

  if (timeline.isError) return <p className="text-[13px] text-u-text3">{messageFor(timeline.error)}</p>;
  if (timeline.isPending) return <LinesSkeleton />;
  if (lines.length === 0) return <p className="text-[13px] text-u-text3">Nothing recorded yet.</p>;
  return (
    <div>
      <ol aria-label="Timeline" className="flex flex-col gap-3 border-s border-dotted border-u-border-strong ps-3.5">
        {cut.visible.map((line) => (
          <li key={line.key}>
            <p className="text-[13px]/[1.5] text-u-text2">
              <span className="font-semibold text-u-text">{line.actorName}</span> {line.text}
            </p>
            <p className="mt-0.5 font-mono text-[11px] text-u-text3">
              <time dateTime={line.occurredAt}>{formatActivityTime(line.occurredAt)}</time>
              {line.detail && ` · ${line.detail}`}
            </p>
          </li>
        ))}
      </ol>
      {cut.isClipped ? (
        cut.seeMore
      ) : (
        hasNextPage && (
          <button
            type="button"
            onClick={() => void fetchNextPage()}
            disabled={isFetchingNextPage}
            className="inline-flex items-center gap-1.5 mt-3 text-note font-medium text-u-accent hover:underline disabled:opacity-60"
          >
            {isFetchingNextPage && <Spinner />}
            Load more
          </button>
        )
      )}
    </div>
  );
}
