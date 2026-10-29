import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import { NoteText } from "../../../notes/components/NoteText";
import type { FieldChange, PersonNote, PersonTimelineEntry } from "../../api/types";
import {
  actorOf,
  asideOf,
  cardTimeOf,
  fieldLabel,
  fullDateTime,
  iconOf,
  isOrigin,
  previewOf,
  shortDateTime,
  verbOf,
  type ActivityPreview,
  actorNameOf,
  type NoteEdit,
  type ActivityExecutive,
  type TaskState,
} from "../../lib/activityPresentation";
import { ActorFace, KindTile } from "./ActivityVisuals";

interface ItemProps {
  entry: PersonTimelineEntry;
  executive: ActivityExecutive;
  projectId: string;
  now: Date;
  onOpen: (entry: PersonTimelineEntry) => void;
  /** The position's readable notes by id, so a note's card can show its opening as written. */
  notes?: ReadonlyMap<string, PersonNote>;
  /** Where each task stands now, so the card that created one shows it ticked once it is done. */
  taskStates?: ReadonlyMap<string, TaskState>;
}

const CARD = cn(
  "group relative flex w-full flex-col gap-2 rounded-xl border bg-u-bg px-3 py-2.5 text-start transition",
  "hover:border-u-border-strong hover:shadow-u-e2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-u-accent-ring",
);

/**
 * Every event is one card on the spine, its kind's tile beside it and who and when on top: a note or a call runs
 * taller with its preview, a tag or a ticked task is the header and one line.
 */
export function ActivityCard({ entry, executive, projectId, now, onOpen, notes, taskStates }: ItemProps) {
  const actor = actorOf(entry);
  const name = actorNameOf(actor, executive);
  const preview = previewOf(entry);
  const note = preview?.type === "note" && entry.details.noteId ? notes?.get(entry.details.noteId) : undefined;
  const taskState = entry.details.taskId ? taskStates?.get(entry.details.taskId) : undefined;
  const aside = preview ? null : asideOf(entry);
  const noteEdit = note ? editOf(note) : preview?.type === "note" ? preview.edited : null;
  return (
    <li className="relative">
      <KindTile icon={iconOf(entry)} size="tile" className="absolute -start-[42px] top-2.5 bg-u-surface" />
      <button
        type="button"
        onClick={() => onOpen(entry)}
        aria-label={`${name} ${verbOf(entry)}${aside ? `, ${aside}` : ""}${noteEdit ? ", edited" : ""}, ${fullDateTime(entry.occurredAt)}. Open details`}
        className={cn(CARD, isOrigin(entry) ? "border-dashed border-u-border-strong" : "border-u-border")}
      >
        <WhoAndWhen entry={entry} executive={executive} projectId={projectId} now={now} />
        {(aside || preview) && (
          <span className="block min-w-0 ps-8">
            {aside && <span className="text-note text-u-text2">{aside}</span>}
            {note && preview?.type === "note" ? (
              <NoteQuickView note={note} aboutThePerson={preview.aboutThePerson} edited={noteEdit} />
            ) : (
              preview && <PreviewOf preview={preview} taskState={taskState} />
            )}
          </span>
        )}
        <span className="pointer-events-none absolute bottom-2.5 end-2.5 text-u-text3 opacity-0 transition group-hover:opacity-100 group-focus-visible:opacity-100">
          <Icon d={ICONS.chevronRight} size={14} />
        </span>
      </button>
    </li>
  );
}

/** One change inside a fold's card: its kind, what it was and when, opening read-only like any card. */
function FoldLine({ entry, executive, onOpen }: Pick<ItemProps, "entry" | "executive" | "onOpen">) {
  const name = actorNameOf(actorOf(entry), executive);
  const aside = asideOf(entry);
  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(entry)}
        aria-label={`${name} ${verbOf(entry)}${aside ? `, ${aside}` : ""}, ${fullDateTime(entry.occurredAt)}. Open details`}
        className="flex w-full min-w-0 items-center gap-2.5 rounded-lg px-2 py-1.5 text-start transition hover:bg-u-raised focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-u-accent-ring"
      >
        <KindTile icon={iconOf(entry)} size="dot" />
        <span className="min-w-0 flex-1 truncate text-note text-u-text2">
          {verbOf(entry).charAt(0).toUpperCase() + verbOf(entry).slice(1)}
          {aside && <span className="text-u-text3"> · {aside}</span>}
        </span>
        <time dateTime={entry.occurredAt} title={fullDateTime(entry.occurredAt)} className="flex-none font-mono text-meta text-u-text3">
          {clockOf(entry.occurredAt)}
        </time>
      </button>
    </li>
  );
}

/** Three or more quick changes by one person, folded to one line that opens in place. */
export function ActivityFold({
  entries,
  isOpen,
  onToggle,
  ...rest
}: Omit<ItemProps, "entry"> & { entries: PersonTimelineEntry[]; isOpen: boolean; onToggle: () => void }) {
  const first = entries[0];
  const last = entries[entries.length - 1];
  const actor = actorOf(first);
  const name = actorNameOf(actor, rest.executive);
  const span = `${clockOf(last.occurredAt)}–${clockOf(first.occurredAt)}`;
  return (
    <li className="relative">
      <KindTile icon={ICONS.position} size="tile" className="absolute -start-[42px] top-2.5 bg-u-surface" />
      <div className="rounded-xl border border-u-border bg-u-bg transition hover:border-u-border-strong">
        <button
          type="button"
          aria-expanded={isOpen}
          aria-label={`${name} made ${entries.length} changes, ${span}`}
          onClick={onToggle}
          className="flex w-full min-w-0 flex-col gap-2 rounded-xl px-3 py-2.5 text-start focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-u-accent-ring"
        >
          <span className="flex min-w-0 items-center gap-2">
            <ActorFace actor={actor} executive={rest.executive} projectId={rest.projectId} size="sm" title={name} />
            <span className="min-w-0 flex-1 text-body text-u-text2">
              <span className="font-semibold text-u-text">{name}</span> made {entries.length} changes
            </span>
            <time dateTime={first.occurredAt} title={fullDateTime(first.occurredAt)} className="flex-none font-mono text-meta text-u-text3">
              {span}
            </time>
          </span>
          <span className="flex items-center gap-1.5 ps-8 text-note text-u-text3">
            {isOpen ? "Hide changes" : summaryOf(entries)}
            <Icon d={ICONS.chevronDown} size={14} className={cn("flex-none transition-transform", isOpen && "rotate-180")} />
          </span>
        </button>
        {isOpen && (
          <ul className="mx-2 mb-2 flex flex-col border-t border-u-border pt-1.5" aria-label={`${name}'s ${entries.length} changes`}>
            {entries.map((entry) => (
              <FoldLine key={entry.id} entry={entry} executive={rest.executive} onOpen={rest.onOpen} />
            ))}
          </ul>
        )}
      </div>
    </li>
  );
}

/** "Tagged them, completed a task and 2 more": what a fold holds, in the order it happened. */
function summaryOf(entries: readonly PersonTimelineEntry[]): string {
  const verbs = [...new Set([...entries].reverse().map((entry) => verbOf(entry)))];
  const named = verbs.slice(0, 2).join(", ");
  const sentence = verbs.length > 2 ? `${named} and ${verbs.length - 2} more` : named;
  return sentence.charAt(0).toUpperCase() + sentence.slice(1);
}

function WhoAndWhen({ entry, executive, projectId, now }: Omit<ItemProps, "onOpen">) {
  const actor = actorOf(entry);
  const name = actorNameOf(actor, executive);
  return (
    <span className="flex min-w-0 items-center gap-2">
      <ActorFace actor={actor} executive={executive} projectId={projectId} size="sm" title={name} />
      <span className="min-w-0 flex-1 text-body text-u-text2">
        <span className="font-semibold text-u-text">{name}</span> {verbOf(entry)}
      </span>
      <time
        dateTime={entry.occurredAt}
        title={fullDateTime(entry.occurredAt)}
        className="flex-none font-mono text-meta text-u-text3"
      >
        {cardTimeOf(entry.occurredAt, now)}
      </time>
    </span>
  );
}

/**
 * A note's opening as it was written — its headings, bullets and checklists, not flattened words — held to a few
 * lines and faded where it runs on; the card opens the whole of it.
 */
function NoteQuickView({
  note,
  aboutThePerson,
  edited,
}: {
  note: PersonNote;
  aboutThePerson: boolean;
  edited: NoteEdit | null;
}) {
  const box = useRef<HTMLSpanElement>(null);
  const [isCut, setIsCut] = useState(false);
  useLayoutEffect(() => {
    const element = box.current;
    if (element) setIsCut(element.scrollHeight > element.clientHeight + 1);
  }, [note.body]);
  return (
    <span className="flex flex-col gap-1.5">
      {/* Inert: a note's links and checklist boxes are not controls inside a card that is itself one button. */}
      <span
        ref={box}
        inert
        className={cn(
          "pointer-events-none block max-h-[5.4rem] overflow-hidden text-u-text",
          "[&_h2]:mt-0 [&_h2]:text-body [&_h3]:mt-0 [&_ul]:my-0.5 [&_ol]:my-0.5 [&_.note-prose]:text-u-text",
          isCut && "[mask-image:linear-gradient(to_bottom,black_60%,transparent)]",
        )}
      >
        <NoteText body={note.body} format={note.format} className="text-body" />
      </span>
      {isCut && <span className="text-note font-medium text-u-accent">Read the whole note</span>}
      <NoteChips aboutThePerson={aboutThePerson} edited={edited} />
    </span>
  );
}

export function PreviewOf({ preview, taskState = "open" }: { preview: ActivityPreview; taskState?: TaskState }) {
  switch (preview.type) {
    case "note":
      return preview.excerpt ? (
        <span className="flex flex-col gap-1.5">
          <span className="line-clamp-2 text-body text-u-text">{preview.excerpt}</span>
          <NoteChips aboutThePerson={preview.aboutThePerson} edited={preview.edited} />
        </span>
      ) : (
        <span className="text-note text-u-text3">This note was deleted.</span>
      );
    case "status":
      return (
        <span className="flex flex-wrap items-center gap-1.5 text-note text-u-text3">
          {preview.from && <StatusPill label={preview.from} />}
          {preview.from && <Icon d={ICONS.arrowRight} size={13} className="text-u-text3" />}
          <StatusPill label={preview.to} isCurrent />
          {preview.cause && <span>{preview.cause}</span>}
        </span>
      );
    case "changes":
      return (
        <span className="flex flex-col gap-1">
          {preview.changes.map((change) => (
            <ChangeLine key={change.field} change={change} />
          ))}
          {preview.hidden > 0 && <span className="text-note text-u-text3">+{preview.hidden} more</span>}
        </span>
      );
    case "fields":
      return <span className="text-note text-u-text3">{preview.labels.join(", ")}</span>;
    case "meeting":
      return <MeetingPreview preview={preview} />;
    case "email":
      return (
        <span className="flex min-w-0 items-center gap-2 rounded-lg border border-u-border bg-u-surface px-2.5 py-1.5 text-note text-u-text2">
          <Icon d={ICONS.mail} size={13} className="flex-none text-u-adjacent" />
          <span className="truncate">{preview.label}</span>
          {preview.detail && <span className="flex-none text-u-text3">· {preview.detail}</span>}
        </span>
      );
    case "document":
      return (
        <span className="flex min-w-0 items-center gap-2.5 rounded-lg border border-u-border bg-u-surface px-2.5 py-1.5">
          <Icon d={ICONS.file} size={16} className="flex-none text-u-offlimits" />
          <span className="min-w-0">
            <span className="block truncate text-body font-medium text-u-text">
              {preview.title ?? `A ${preview.noun} that has since been deleted`}
            </span>
            <span className="block text-meta text-u-text3">
              {preview.noun.charAt(0).toUpperCase() + preview.noun.slice(1)}
              {preview.version && ` · Version ${preview.version}`}
            </span>
          </span>
        </span>
      );
    case "task":
      return <TaskPreview preview={preview} state={taskState} />;
    case "origin":
      return (
        <span className="flex flex-col gap-1 text-note text-u-text3">
          {preview.door && <span>{preview.door}</span>}
          {preview.mapped && <span>Already in your candidates, so added rather than duplicated</span>}
        </span>
      );
    case "assessment":
      return <Chip tone="ai">{preview.label}</Chip>;
  }
}

const TASK_STATE_WORDS: Record<TaskState, string | null> = { open: null, done: "Done", cancelled: "Cancelled" };

function TaskPreview({ preview, state }: { preview: Extract<ActivityPreview, { type: "task" }>; state: TaskState }) {
  const meta = [
    TASK_STATE_WORDS[state],
    shortDateTime(preview.dueAt) && `Due ${shortDateTime(preview.dueAt)}`,
    preview.assignee && `Assigned to ${preview.assignee}`,
  ].filter(Boolean);
  return (
    <span className="flex items-center gap-2 text-body">
      <span
        role="img"
        aria-label={TASK_STATE_WORDS[state] ?? "Open"}
        className={cn(
          "grid size-[15px] flex-none place-items-center rounded-sm border-[1.5px]",
          state === "done" && "border-u-direct bg-u-direct text-u-surface",
          state === "cancelled" && "border-u-border-strong",
          state === "open" && "border-u-chart-5",
        )}
      >
        {state === "done" && <Icon d={ICONS.check} size={10} />}
      </span>
      <span className="min-w-0">
        <span className={cn("block truncate", state === "open" ? "text-u-text" : "text-u-text3 line-through")}>
          {preview.title}
        </span>
        {meta.length > 0 && <span className="block text-note text-u-text3">{meta.join(" · ")}</span>}
      </span>
    </span>
  );
}

function MeetingPreview({ preview }: { preview: Extract<ActivityPreview, { type: "meeting" }> }) {
  const at = new Date(preview.startsAt);
  const isValid = !Number.isNaN(at.getTime());
  return (
    <span className="flex items-center gap-2.5">
      {isValid && (
        <span className="w-10 flex-none overflow-hidden rounded-lg border border-u-border-strong bg-u-surface text-center" aria-hidden="true">
          <span className={cn("block py-0.5 text-eyebrow font-semibold uppercase tracking-[0.08em]", preview.cancelled ? "bg-u-offlimits-tint text-u-offlimits" : "bg-u-direct-tint text-u-direct")}>
            {at.toLocaleDateString("en-GB", { month: "short" })}
          </span>
          <span className="block pb-1 pt-0.5 font-mono text-body font-medium text-u-text">{at.getDate()}</span>
        </span>
      )}
      <span className="min-w-0 text-body">
        <span className={cn("block", preview.cancelled ? "text-u-text3 line-through" : "text-u-text")}>
          {shortDateTime(preview.startsAt) ?? "A call"}
        </span>
        {preview.previousStartsAt && (
          <span className="block text-note text-u-text3">Was {shortDateTime(preview.previousStartsAt)}</span>
        )}
      </span>
    </span>
  );
}

export function ChangeLine({ change }: { change: FieldChange }) {
  if (change.from === null && change.to === null) {
    return (
      <span className="text-note text-u-text2">
        <span className="text-u-text3">{fieldLabel(change.field)}</span> edited
      </span>
    );
  }
  return (
    <span className="grid grid-cols-[7.5em_minmax(0,1fr)] items-baseline gap-2 text-note">
      <span className="truncate text-u-text3">{fieldLabel(change.field)}</span>
      <span className="flex min-w-0 flex-wrap items-baseline gap-x-1.5">
        <span className="truncate text-u-text3 line-through decoration-u-offlimits">{change.from ?? "—"}</span>
        <span className="text-u-text3" aria-label="changed to">→</span>
        <span className="truncate font-medium text-u-text">{change.to ?? "Cleared"}</span>
      </span>
    </span>
  );
}

export function StatusPill({ label, isCurrent = false }: { label: string; isCurrent?: boolean }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-px text-note font-medium",
        isCurrent ? "bg-u-direct-tint text-u-direct" : "bg-u-raised text-u-text2",
      )}
    >
      {label}
    </span>
  );
}

/** A note's line is its only one, so it says when the words it shows are not the ones first written. */
function NoteChips({ aboutThePerson, edited }: { aboutThePerson: boolean; edited: NoteEdit | null }) {
  if (!aboutThePerson && !edited) return null;
  return (
    <span className="flex flex-wrap gap-1.5">
      {edited && (
        <Chip title={`Edited by ${edited.byName ?? "someone"} · ${fullDateTime(edited.at)}`}>Edited</Chip>
      )}
      {aboutThePerson && <Chip>About the person · every position</Chip>}
    </span>
  );
}

function Chip({ children, tone, title }: { children: ReactNode; tone?: "ai"; title?: string }) {
  return (
    <span
      title={title}
      className={cn(
        "inline-flex w-fit items-center rounded-full px-2 py-px text-meta font-medium",
        tone === "ai" ? "bg-u-inferred-tint text-u-inferred" : "bg-u-raised text-u-text2",
      )}
    >
      {children}
    </span>
  );
}

function editOf(note: PersonNote): NoteEdit | null {
  return note.editedAt ? { at: note.editedAt, byName: note.editedByName } : null;
}

function clockOf(isoInstant: string): string {
  const at = new Date(isoInstant);
  return Number.isNaN(at.getTime()) ? "—" : at.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}
