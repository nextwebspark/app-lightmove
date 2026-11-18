import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ActionMenu, MenuItem } from "../../../components/ui/ActionMenu";
import { Avatar } from "../../../components/ui/Avatar";
import { Button } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import type { NoteOutcome, PersonNote, PersonNoteKind } from "../api/types";
import { shortWhen } from "../lib/candidateActivity";
import { happenedAtOf, isContactNote, NOTE_KINDS, noteKindLabel } from "../lib/noteKinds";
import { outcomeLabel, outcomeQuestionOf, outcomesOf } from "../lib/noteOutcomes";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { NoteKindMark } from "./NoteKindMark";
import { ClampedNoteText } from "../../notes/components/ClampedNoteText";
import { NoteReaderModal } from "../../notes/components/NoteReaderModal";
import { NOTE_MAX_STORED, NOTE_TOO_LONG } from "../../tasks/lib/mentions";
import type { TeammateOption } from "../../tasks/lib/team";
import { formatFull, formatInstantTime } from "../../../lib/datetime";

/** Who reads a note written inside a position: that position's team, or every position's. */
export type NoteScope = "position" | "everywhere";

export function NoteKindPicker({
  value,
  onChange,
  kinds,
  label = "Kind of note",
}: {
  value: PersonNoteKind;
  onChange: (kind: PersonNoteKind) => void;
  /** The kinds offered, in the composer's order; every kind when left out. */
  kinds?: readonly PersonNoteKind[];
  label?: string;
}) {
  const options = kinds ? KIND_OPTIONS.filter((option) => kinds.includes(option.value)) : KIND_OPTIONS;
  return <NoteChoiceChips label={label} options={options} value={value} onChange={onChange} className="mb-2" />;
}

const KIND_OPTIONS = NOTE_KINDS.map((option) => ({
  ...option,
  mark: <NoteKindMark kind={option.value} size={12} />,
}));

/** Inside a position a note is about it or about the person on every position, never about another position. */
export function NoteScopePicker({
  positionTitle,
  value,
  onChange,
}: {
  positionTitle: string | null | undefined;
  value: NoteScope;
  onChange: (scope: NoteScope) => void;
}) {
  return (
    <NoteChoiceChips
      label="Who reads this note"
      options={[
        { value: "position", label: "This position", title: positionTitle ?? undefined },
        { value: "everywhere", label: "All positions" },
      ]}
      value={value}
      onChange={onChange}
    />
  );
}

/**
 * How a logged call or WhatsApp message went: optional, so a second press of the chosen one takes it back. Once one is
 * chosen the note may stay empty.
 */
export function NoteOutcomePicker({
  kind,
  value,
  onChange,
}: {
  kind: PersonNoteKind;
  value: NoteOutcome | null;
  onChange: (outcome: NoteOutcome | null) => void;
}) {
  const options = outcomesOf(kind) ?? [];
  const keys = useRadioGroupKeys(
    options.map((option) => option.value),
    value,
    onChange,
  );
  if (options.length === 0) return null;
  const question = outcomeQuestionOf(kind);
  return (
    <div>
      <div className="mb-1.5 text-meta font-medium text-u-text3">{question}</div>
      <div
        ref={keys.ref}
        role="radiogroup"
        aria-label={question}
        onKeyDown={keys.onKeyDown}
        className="flex flex-wrap gap-1.5"
      >
        {options.map((option, index) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={value === option.value}
            tabIndex={value === option.value || (value === null && index === 0) ? 0 : -1}
            onClick={() => onChange(value === option.value ? null : option.value)}
            className={choiceChipClassOf(value === option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>
    </div>
  );
}

/** How a logged call or message went, beside its kind on a card. */
export function NoteOutcomePill({ outcome, className }: { outcome: NoteOutcome; className?: string }) {
  return (
    <span className={cn("flex-none rounded-full bg-u-raised px-2 py-px text-meta text-u-text2", className)}>
      {outcomeLabel(outcome)}
    </span>
  );
}

/** The composer's chips: a radio group with one tab stop, the arrows moving the choice. */
function NoteChoiceChips<TValue extends string>({
  label,
  options,
  value,
  onChange,
  className,
}: {
  label: string;
  options: readonly { value: TValue; label: string; title?: string; mark?: ReactNode }[];
  value: TValue;
  onChange: (value: TValue) => void;
  className?: string;
}) {
  const keys = useRadioGroupKeys(
    options.map((option) => option.value),
    value,
    onChange,
  );
  return (
    <div
      ref={keys.ref}
      role="radiogroup"
      aria-label={label}
      onKeyDown={keys.onKeyDown}
      className={cn("flex flex-wrap gap-1.5", className)}
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          tabIndex={value === option.value ? 0 : -1}
          title={option.title}
          onClick={() => onChange(option.value)}
          className={choiceChipClassOf(value === option.value)}
        >
          {option.mark}
          {option.label}
        </button>
      ))}
    </div>
  );
}

function choiceChipClassOf(isChosen: boolean): string {
  return cn(
    "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 font-mono text-[11px] transition",
    isChosen ? "border-u-accent bg-u-accent-tint text-u-accent" : "border-u-border text-u-text3 hover:text-u-text2",
  );
}

/** The id a note's card carries, so a task set from it can bring it into view. */
export function noteAnchorOf(noteId: string): string {
  return `note-${noteId}`;
}

/**
 * A note as a card draws it: a person's, which may be about a position, or a company's, which never is. The two
 * share every other field.
 */
export type NoteCardNote = Omit<PersonNote, "projectId" | "projectTitle"> &
  Partial<Pick<PersonNote, "projectId" | "projectTitle">>;

/** What an edit changed: the words, the outcome (`""` clears it), or both; what it leaves out stays as it is. */
export interface NoteRevision {
  body?: string;
  outcome?: NoteOutcome | "";
}

/** What a card may do to its note; a card drawn without them only reads. */
export interface NoteCardActions {
  onPin: () => void;
  onRevise: (change: NoteRevision) => void;
  onRemove: () => void;
  busy: boolean;
}

/**
 * One note as both drawers draw it: author, kind, pinned, when, the words, and what it is about. With
 * actions it offers Pin to anyone and Edit and Delete to whoever may change it; Delete asks first, a stray click
 * on a note being too easy, and is still taken back with the toast's Undo.
 */
/** A timeline's head for a note: the kind's mark leading, the note's heading, and its author beneath. */
export interface NoteCardLead {
  mark: ReactNode;
  heading: string;
}

export function NoteCard({
  note,
  actions,
  teammates = null,
  flashAt = null,
  showsKindMark = true,
  lead = null,
  namesPosition = true,
  takesOutcome = false,
}: {
  note: NoteCardNote;
  actions?: NoteCardActions;
  /** The team an edit may mention: the position the note is about; null for a note about no position. */
  teammates?: readonly TeammateOption[] | null;
  /**
   * Brought into view from the task set from it: an accent wash that fades, played again for each new value. The
   * wash is its own element so the card keeps its identity, and with it the keyboard focus it was given.
   */
  flashAt?: number | null;
  /** Off on a timeline whose spine already draws the kind's mark: the card keeps the kind's word alone. */
  showsKindMark?: boolean;
  lead?: NoteCardLead | null;
  /** Off inside a position, where "About" would only repeat the position being read. */
  namesPosition?: boolean;
  /** A person's call or WhatsApp log, whose edit may change how it went; a company's note takes no outcome. */
  takesOutcome?: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [confirmingRemove, setConfirmingRemove] = useState(false);
  const [reading, setReading] = useState(false);
  const [draft, setDraft] = useState(note.body);
  const [outcome, setOutcome] = useState<NoteOutcome | null>(note.outcome ?? null);
  const editsOutcome = takesOutcome && outcomesOf(note.kind) !== null;
  const revised = draft.trim();
  const revisedTooLong = revised.length > NOTE_MAX_STORED;
  const canSave = (revised.length > 0 || (editsOutcome && outcome !== null)) && !revisedTooLong;
  const startEditing = () => {
    setDraft(note.body);
    setOutcome(note.outcome ?? null);
    setEditing(true);
  };
  const authorName = note.authorName ?? "Someone";
  const saveRevision = () => {
    if (!actions || !canSave) return;
    const change: NoteRevision = {};
    // An untouched draft is still the stored body; sent back as Markdown, a plain note's "*" and "1." would format.
    if (draft !== note.body) change.body = revised;
    if (editsOutcome && outcome !== (note.outcome ?? null)) change.outcome = outcome ?? "";
    if (change.body !== undefined || change.outcome !== undefined) actions.onRevise(change);
    setEditing(false);
  };

  const outcomePill = note.outcome && <NoteOutcomePill outcome={note.outcome} />;
  // A timeline's call or message logged with only how it went reads as one line: the outcome is what was said.
  const outcomeOnly = lead && !editing && note.outcome && !note.body.trim() ? { lead, outcome: note.outcome } : null;
  const noteActions: NoteAction[] = actions
    ? [
        { label: note.pinned ? "Unpin" : "Pin", icon: ICONS.pin, active: note.pinned, run: actions.onPin },
        ...(note.editable && !editing ? [{ label: "Edit", icon: ICONS.pencil, run: startEditing }] : []),
        ...(note.editable ? [{ label: "Delete", icon: ICONS.trash, danger: true, run: () => setConfirmingRemove(true) }] : []),
      ]
    : [];
  const scopeText =
    note.projectTitle && namesPosition ? `About ${note.projectTitle}` : note.projectId === null ? "All positions" : null;
  const footerMeta = (scopeText || note.editedAt) && (
    <>
      {scopeText && <span>{scopeText}</span>}
      {note.editedAt && (
        <span title={formatFull(note.editedAt) ?? undefined}>Edited by {note.editedByName ?? "someone"}</span>
      )}
    </>
  );
  const pinnedMark = note.pinned && (
    <span className="flex items-center gap-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-accent">
      <Icon d={ICONS.pin} size={10} />
      Pinned
    </span>
  );
  // A log is dated by when it happened; the hover says when it was written, where that differs.
  const when = (
    <time
      dateTime={happenedAtOf(note)}
      title={
        happenedAtOf(note) === note.createdAt
          ? (formatFull(note.createdAt) ?? undefined)
          : `Happened ${formatFull(happenedAtOf(note)) ?? "—"} · logged ${formatFull(note.createdAt) ?? "—"}`
      }
      className="ms-auto flex-none font-mono text-[11px] text-u-text3"
    >
      {isContactNote(note.kind) ? whenWithClock(happenedAtOf(note)) : shortWhen(note.createdAt)}
    </time>
  );

  return (
    <li
      id={noteAnchorOf(note.id)}
      tabIndex={-1}
      className={cn(
        "relative rounded-[8px] border px-3 py-2.5 outline-none",
        note.pinned ? "border-u-accent/60 bg-u-raised" : "border-u-border",
      )}
    >
      {flashAt !== null && (
        <span
          key={flashAt}
          aria-hidden
          className="animate-note-flash pointer-events-none absolute -inset-px rounded-[inherit] border border-transparent"
        />
      )}
      {outcomeOnly ? (
        <div className="flex items-start gap-2.5">
          {outcomeOnly.lead.mark}
          <div className="min-w-0 flex-1 self-center">
            <div className="flex items-center gap-2.5">
              <span className="min-w-0 flex-1 truncate text-body">
                <span className="font-semibold text-u-text">{outcomeOnly.lead.heading}</span>
                <span className="text-u-text2"> · {outcomeLabel(outcomeOnly.outcome)}</span>
              </span>
              {pinnedMark}
              <span className="flex flex-none items-center gap-1.5">
                <Avatar id={note.authorUserId} name={authorName} src={note.authorAvatarUrl} size="xs" title={authorName} />
                <span className="sr-only">{authorName}</span>
                {when}
              </span>
              {actions && <NoteActionMenu items={noteActions} busy={actions.busy} />}
            </div>
            {footerMeta && <div className={cn("mt-1", NOTE_META)}>{footerMeta}</div>}
          </div>
        </div>
      ) : lead ? (
        <div className="flex items-start gap-2.5">
          {lead.mark}
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline gap-2">
              <span className="min-w-0 flex-1 text-body font-semibold text-u-text">{lead.heading}</span>
              {outcomePill}
              {pinnedMark}
              <span className="ms-auto flex flex-none items-center gap-1.5 self-center">
                <Avatar id={note.authorUserId} name={authorName} src={note.authorAvatarUrl} size="xs" title={authorName} />
                <span className="sr-only">{authorName}</span>
                {when}
              </span>
            </div>
          </div>
        </div>
      ) : (
        <div className="flex items-center gap-2">
          <Avatar id={note.authorUserId} name={authorName} src={note.authorAvatarUrl} size="sm" />
          <span className="text-[12.5px] font-semibold">{authorName}</span>
          {/* A plain note is the Notes tab's only kind, so it carries no tag; a call or a LinkedIn or WhatsApp message does. */}
          {note.kind !== "general" && (
            <span className="flex items-center gap-1 text-u-text3">
              {showsKindMark && <NoteKindMark kind={note.kind} size={12} />}
              <span className="rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.06em]">
                {noteKindLabel(note.kind)}
              </span>
            </span>
          )}
          {outcomePill}
          {pinnedMark}
          {when}
        </div>
      )}
      {editing && actions ? (
        // Not a form: the editor's link row is one, and a nested form's submit would save the note with it.
        <div className="mt-2 rounded-u-control border border-u-border p-2">
          <NoteEditor
            value={draft}
            format={note.format}
            onChange={setDraft}
            teammates={teammates}
            ariaLabel="Edit note"
            onSubmit={saveRevision}
            canSubmit={canSave}
            expandTitle="Edit note"
            autoFocus
            header={
              editsOutcome ? <NoteOutcomePicker kind={note.kind} value={outcome} onChange={setOutcome} /> : undefined
            }
            footer={
              <>
                {revisedTooLong && (
                  <p role="alert" className="mb-2 text-meta text-u-offlimits">
                    {NOTE_TOO_LONG}
                  </p>
                )}
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="secondary" className="px-3 py-1 text-[12px]" onClick={() => setEditing(false)}>
                    Cancel
                  </Button>
                  <Button
                    type="button"
                    className="px-3 py-1 text-[12px]"
                    disabled={!canSave}
                    loading={actions.busy}
                    onClick={saveRevision}
                  >
                    Save
                  </Button>
                </div>
              </>
            }
          />
        </div>
      ) : (
        <>
        {note.title && !lead && <p className="mt-1.5 text-body font-semibold text-u-text">{note.title}</p>}
        {note.body.trim() && (
          <ClampedNoteText
            body={note.body}
            format={note.format}
            className="mt-1.5 text-[13px]/[1.55] text-u-text2"
            onOpen={() => setReading(true)}
            defaultExpanded={flashAt !== null}
          />
        )}
        </>
      )}
      {!outcomeOnly && (
        <div className={cn("mt-1.5", NOTE_META)}>
          {footerMeta}
          {noteActions.length > 0 && actions && (
            <span className="-my-1 ms-auto flex items-center gap-0.5">
              {noteActions.map((action) => (
                <NoteActionButton
                  key={action.label}
                  label={action.label}
                  icon={action.icon}
                  active={action.active}
                  danger={action.danger}
                  onClick={action.run}
                  disabled={actions.busy}
                />
              ))}
            </span>
          )}
        </div>
      )}
      <NoteReaderModal
        open={reading}
        onClose={() => setReading(false)}
        title={note.kind === "general" ? "Note" : `${noteKindLabel(note.kind)} note`}
        details={[
          authorName,
          note.projectTitle && namesPosition
            ? `About ${note.projectTitle}`
            : note.projectId === null
              ? "All positions"
              : null,
        ]}
        writtenAt={note.createdAt}
        body={note.body}
        format={note.format}
        onEdit={
          actions && note.editable
            ? () => {
                setReading(false);
                startEditing();
              }
            : undefined
        }
      />
      {actions && (
        <ConfirmDialog
          open={confirmingRemove}
          title="Delete this note?"
          confirmLabel="Delete note"
          onConfirm={() => {
            setConfirmingRemove(false);
            actions.onRemove();
          }}
          onClose={() => setConfirmingRemove(false)}
        >
          <p>It is removed for everyone on the team. You can undo it straight after.</p>
        </ConfirmDialog>
      )}
    </li>
  );
}

const NOTE_META = "flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-u-text3";

/** Pin, Edit and Delete as a card offers them, drawn as its footer's buttons or behind a one-line card's ⋯. */
interface NoteAction {
  label: string;
  icon: string;
  active?: boolean;
  danger?: boolean;
  run: () => void;
}

function NoteActionMenu({ items, busy }: { items: readonly NoteAction[]; busy: boolean }) {
  return (
    <ActionMenu label="Note options" width={180}>
      {(close) =>
        items.map((item) => (
          <MenuItem
            key={item.label}
            icon={item.icon}
            tone={item.danger ? "danger" : undefined}
            disabled={busy}
            onSelect={() => {
              close();
              item.run();
            }}
          >
            {item.label}
          </MenuItem>
        ))
      }
    </ActionMenu>
  );
}

function NoteActionButton({
  label,
  icon,
  active = false,
  danger = false,
  disabled,
  onClick,
}: {
  label: string;
  icon: string;
  active?: boolean;
  danger?: boolean;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "grid size-9 place-items-center rounded-u-control text-u-text3 transition hover:bg-u-raised disabled:opacity-50 lg:size-7",
        danger ? "hover:text-u-offlimits" : "hover:text-u-text",
        active && "text-u-accent hover:text-u-accent",
      )}
    >
      <Icon d={icon} size={14} />
    </button>
  );
}

/** "3 Nov · 15:20": a logged call or meeting is read by the hour it happened, not only the day. */
function whenWithClock(isoInstant: string): string {
  const clock = formatInstantTime(isoInstant);
  if (!clock) return "—";
  const day = shortWhen(isoInstant);
  return day.startsWith("Today") ? day : `${day} · ${clock}`;
}
