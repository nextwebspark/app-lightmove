import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, TextArea, useToast } from "../../../components/ui";
import { CollapsibleSection } from "../../../components/ui/CollapsibleSection";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import { useSubmitShortcut } from "../../../lib/useSubmitShortcut";
import { formatActivityTime } from "../../projects/lib/activity";
import * as personCrmApi from "../api/personCrmApi";
import * as poolApi from "../api/poolApi";
import type { PersonNoteKind, PersonPosition } from "../api/types";
import { timelineLines } from "../lib/candidateActivity";
import { candidateStatusStyle } from "../lib/candidateVocabulary";
import { NoteCard, NoteKindPicker } from "./NoteParts";

/**
 * The shared person in a position's drawer — where else they are mapped, the notes on them, and what
 * has been done to them — as drawn in `Position.dc.html`. Staff-only: the drawer renders none of these
 * for a client seat, and none of them asks the server for anything until it is rendered.
 */

interface PersonSectionProps {
  projectId: string;
  candidateId: string;
  open: boolean;
  onToggle: () => void;
}

function usePositions(projectId: string, candidateId: string) {
  return useQuery({
    queryKey: personCrmApi.PERSON_POSITIONS_KEY(projectId, candidateId),
    queryFn: ({ signal }) => personCrmApi.getPersonPositions(projectId, candidateId, signal),
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

export function PositionsSection({ projectId, candidateId, open, onToggle }: PersonSectionProps) {
  const positions = usePositions(projectId, candidateId);
  const rows = positions.data ?? [];
  const others = rows.filter((row) => row.projectId !== projectId);

  return (
    <CollapsibleSection
      id="positions"
      open={open}
      onToggle={onToggle}
      title="Positions"
      count={rows.length > 0 ? rows.length : undefined}
      summary={
        positions.isSuccess
          ? others.length > 0
            ? `Also in ${others.map((row) => row.positionTitle ?? "a position").join(", ")}`
            : "Only this position"
          : null
      }
    >
      {positions.isError ? (
        <p className="text-[13px] text-u-text3">{messageFor(positions.error)}</p>
      ) : positions.isPending ? (
        <p className="text-[13px] text-u-text3">Loading…</p>
      ) : (
        <ul className="flex flex-col gap-2 pb-3">
          {rows.map((row) => (
            <PositionCard key={row.candidateId} position={row} current={row.projectId === projectId} />
          ))}
        </ul>
      )}
    </CollapsibleSection>
  );
}

function PositionCard({ position, current }: { position: PersonPosition; current: boolean }) {
  const status = candidateStatusStyle(position.status);
  return (
    <li
      className={cn(
        "rounded-[8px] border px-3 py-2.5",
        current ? "border-u-accent bg-u-accent-tint/40" : "border-u-border",
      )}
    >
      <div className="flex items-center gap-2">
        <span className="min-w-0 flex-1 truncate text-[13px] font-semibold">
          {position.positionTitle ?? "Untitled position"}
        </span>
        {current && (
          <span className="flex-none rounded-[4px] bg-u-accent-tint px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.06em] text-u-accent">
            This position
          </span>
        )}
      </div>
      <div className="mt-1 flex items-center gap-2">
        <span className="min-w-0 flex-1 truncate font-mono text-[11px] text-u-text3">
          Added by {position.addedByName ?? "someone"} · {formatInstantDate(position.addedAt)}
        </span>
        <span
          className={cn(
            "flex-none rounded-full px-2 py-px font-mono text-[10px] font-semibold",
            status.className,
          )}
        >
          {status.label}
        </span>
      </div>
    </li>
  );
}

export function NotesSection({ projectId, candidateId, open, onToggle }: PersonSectionProps) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const notes = useQuery({
    queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
    queryFn: ({ signal }) => personCrmApi.getPersonNotes(projectId, candidateId, signal),
  });
  const positions = usePositions(projectId, candidateId);
  const thisPosition = positions.data?.find((row) => row.projectId === projectId)?.positionTitle;
  const [kind, setKind] = useState<PersonNoteKind>("general");
  const [text, setText] = useState("");

  const saving = useMutation({
    mutationFn: () => personCrmApi.writePersonNote(projectId, candidateId, { kind, body: text.trim() }),
    onSuccess: () => {
      setText("");
      toast("Note saved — your team sees it on every position");
      void queryClient.invalidateQueries({
        queryKey: personCrmApi.PERSON_NOTES_KEY(projectId, candidateId),
      });
      void queryClient.invalidateQueries({
        queryKey: personCrmApi.PERSON_TIMELINE_KEY(projectId, candidateId),
      });
    },
    onError: (error) => toast(messageFor(error)),
  });
  const canSave = text.trim().length > 0 && !saving.isPending;
  const handleKeyDown = useSubmitShortcut(() => canSave && saving.mutate());
  const list = notes.data ?? [];

  return (
    <CollapsibleSection
      id="notes"
      open={open || text.length > 0}
      onToggle={onToggle}
      title="Notes"
      count={list.length > 0 ? list.length : undefined}
      summary={notes.isSuccess ? (firstLineOf(list[0]?.body) ?? "None yet") : null}
    >
      <div className="flex flex-col gap-3 pb-3">
        <div className="rounded-[8px] border border-u-border p-2.5">
          <NoteKindPicker value={kind} onChange={setKind} />
          <TextArea
            value={text}
            onChange={(event) => setText(event.target.value)}
            onKeyDown={handleKeyDown}
            rows={2}
            maxLength={4000}
            aria-label="New note"
            placeholder="What was said, and what happens next…"
            className="font-sans text-[13px]/[1.55]"
          />
          <div className="mt-2 flex items-center gap-2">
            <span className="min-w-0 flex-1 truncate font-mono text-[11px] text-u-text3">
              About {thisPosition ?? "this position"}
            </span>
            <Button
              type="button"
              className="px-3 py-1.5 text-[12.5px]"
              disabled={!canSave}
              loading={saving.isPending}
              onClick={() => saving.mutate()}
            >
              Save note
            </Button>
          </div>
        </div>

        {notes.isError ? (
          <p className="text-[13px] text-u-text3">{messageFor(notes.error)}</p>
        ) : notes.isPending ? (
          <p className="text-[13px] text-u-text3">Loading…</p>
        ) : list.length === 0 ? (
          <p className="text-[13px]/[1.6] text-u-text3">
            No notes yet. A note you save here is shared with the team on every position this person
            is in.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {list.map((note) => (
              <NoteCard key={note.id} note={note} />
            ))}
          </ul>
        )}
      </div>
    </CollapsibleSection>
  );
}

export function TimelineSection({ projectId, candidateId, open, onToggle }: PersonSectionProps) {
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
  const more = hasNextPage ? "+" : "";

  return (
    <CollapsibleSection
      id="timeline"
      open={open}
      onToggle={onToggle}
      title="Timeline"
      count={lines.length > 0 ? `${lines.length}${more}` : undefined}
      summary={
        timeline.isSuccess
          ? `${lines.length}${more} ${lines.length === 1 ? "entry" : "entries"} · who did what, and when`
          : null
      }
    >
      {timeline.isError ? (
        <p className="pb-3 text-[13px] text-u-text3">{messageFor(timeline.error)}</p>
      ) : timeline.isPending ? (
        <p className="pb-3 text-[13px] text-u-text3">Loading…</p>
      ) : lines.length === 0 ? (
        <p className="pb-3 text-[13px] text-u-text3">Nothing recorded yet.</p>
      ) : (
        <div className="pb-3">
          <ol className="flex flex-col gap-3 border-s border-dotted border-u-border-strong ps-3.5">
            {lines.map((line) => (
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
          {hasNextPage && (
            <button
              type="button"
              onClick={() => void fetchNextPage()}
              disabled={isFetchingNextPage}
              className="mt-3 text-note font-medium text-u-accent hover:underline disabled:opacity-60"
            >
              {isFetchingNextPage ? "Loading…" : "Load more"}
            </button>
          )}
        </div>
      )}
    </CollapsibleSection>
  );
}

function firstLineOf(text: string | undefined, max = 100): string | null {
  if (!text) return null;
  const line = text.split("\n", 1)[0].trim();
  return line.length > max ? `${line.slice(0, max - 1).trimEnd()}…` : line || null;
}
