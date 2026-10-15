import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Button, LinesSkeleton, Spinner, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useSubmitShortcut } from "../../../lib/useSubmitShortcut";
import { formatActivityTime } from "../../projects/lib/activity";
import type { TaskNote } from "../../tasks/api/types";
import { MentionTextArea } from "../../tasks/components/MentionTextArea";
import {
  mentionsPresent,
  NOTE_MAX_STORED,
  NOTE_TOO_LONG,
  serializeMentions,
  type Mention,
} from "../../tasks/lib/mentions";
import { usePositionTeam } from "../../tasks/lib/usePositionTeam";
import * as personCrmApi from "../api/personCrmApi";
import * as poolApi from "../api/poolApi";
import type { PersonNote, PersonNoteKind } from "../api/types";
import { timelineLines } from "../lib/candidateActivity";
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
  const queryClient = useQueryClient();
  const toast = useToast();
  const team = usePositionTeam(projectId);
  const notes = useQuery({
    queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
    queryFn: ({ signal }) => personCrmApi.getPersonNotes(projectId, candidateId, signal),
  });
  const positions = usePositions(projectId, candidateId);
  const thisPosition = positions.data?.find((row) => row.projectId === projectId)?.positionTitle;
  const [kind, setKind] = useState<PersonNoteKind>("general");
  const [scope, setScope] = useState<NoteScope>("position");
  const [text, setText] = useState("");
  const [mentions, setMentions] = useState<Mention[]>([]);
  const forEveryPosition = scope === "everywhere";

  const changeScope = (next: NoteScope) => {
    setScope(next);
    setMentions([]);
  };

  const body = forEveryPosition ? text.trim() : serializeMentions(text.trim(), mentionsPresent(text, mentions));
  const tooLong = body.length > NOTE_MAX_STORED;
  const saving = useMutation({
    mutationFn: (_thenNextAction: boolean) =>
      personCrmApi.writePersonNote(
        projectId,
        candidateId,
        forEveryPosition ? { kind, body, allPositions: true } : { kind, body },
      ),
    onSuccess: (note, thenNextAction) => {
      setText("");
      setMentions([]);
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
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const canSave = text.trim().length > 0 && !tooLong && !saving.isPending;
  const handleKeyDown = useSubmitShortcut(() => canSave && saving.mutate(false));
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
        <div className="rounded-[8px] border border-u-border p-2.5">
          <NoteKindPicker value={kind} onChange={setKind} />
          <MentionTextArea
            value={text}
            onChange={setText}
            mentions={mentions}
            onMentionsChange={setMentions}
            teammates={forEveryPosition ? null : team.teammates}
            onKeyDown={handleKeyDown}
            rows={2}
            maxLength={NOTE_MAX_STORED}
            ariaLabel="New note"
            placeholder="What was said, and what happens next…"
            className="font-sans text-body"
          />
          {tooLong && (
            <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
              {NOTE_TOO_LONG}
            </p>
          )}
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <div className="min-w-0 flex-1">
              <NoteScopePicker positionTitle={thisPosition} value={scope} onChange={changeScope} />
            </div>
            {onSetNextAction && (
              <Button
                type="button"
                size="xs"
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
              size="xs"
              disabled={!canSave}
              loading={saving.isPending && !saving.variables}
              onClick={() => saving.mutate(false)}
            >
              Save note
            </Button>
          </div>
        </div>

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
              <NoteCard key={note.id} note={note} flashAt={focus?.noteId === note.id ? focus.at : null} />
            ))}
          </ul>
        )}
      </div>
    </section>
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
