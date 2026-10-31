// Ported from apps/web/src/features/candidates/components/NoteParts.tsx; keep the two in step.
import { useState, type ReactNode } from "react";
import type { NoteKind, NoteRecord } from "../../../api/types";
import { Avatar } from "../../components/Avatar";
import { Button } from "../../components/Button";
import { Icon } from "../../components/Icon";
import { cn } from "../../lib/cn";
import { ICONS } from "../../lib/icons";
import { shortWhen } from "../../lib/shortWhen";
import { useRadioGroupKeys } from "../../lib/useRadioGroupKeys";
import { FIELD_LIMITS } from "../../../api/fieldLimits";
import { NOTE_KINDS, noteKindLabel } from "../lib/noteKinds";
import type { TeammateOption } from "../lib/teammates";
import { ClampedNoteText } from "./ClampedNoteText";
import { NoteKindMark } from "./NoteKindMark";
import { NoteEditor } from "./NoteEditor";

/** Who reads a note written inside a position: that position's team, or every position's. */
export type NoteScope = "position" | "everywhere";

export const NOTE_TOO_LONG = "Too long to save. Shorten it a little.";

export function NoteKindPicker({ value, onChange }: { value: NoteKind; onChange: (kind: NoteKind) => void }) {
  return <NoteChoiceChips label="Kind of note" options={KIND_OPTIONS} value={value} onChange={onChange} />;
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
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-meta text-u-text3">Who reads it</span>
      <NoteChoiceChips
        label="Who reads this note"
        options={[
          { value: "position", label: "This position", title: positionTitle ?? undefined },
          { value: "everywhere", label: "All positions" },
        ]}
        value={value}
        onChange={onChange}
      />
    </div>
  );
}

/** The composer's chips: a radio group with one tab stop, the arrows moving the choice. */
function NoteChoiceChips<TValue extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: readonly { value: TValue; label: string; title?: string; mark?: ReactNode }[];
  value: TValue;
  onChange: (value: TValue) => void;
}) {
  const keys = useRadioGroupKeys(
    options.map((option) => option.value),
    value,
    onChange,
  );
  return (
    <div ref={keys.ref} role="radiogroup" aria-label={label} onKeyDown={keys.onKeyDown} className="flex flex-wrap gap-1.5">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          tabIndex={value === option.value ? 0 : -1}
          title={option.title}
          onClick={() => onChange(option.value)}
          className={cn(
            "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 font-mono text-[11px] transition",
            value === option.value
              ? "border-u-accent bg-u-accent-tint text-u-accent"
              : "border-u-border text-u-text3 hover:text-u-text2",
          )}
        >
          {option.mark}
          {option.label}
        </button>
      ))}
    </div>
  );
}

/** What a card may do to its note; a card drawn without them only reads. */
export interface NoteCardActions {
  onPin: () => void;
  onRevise: (body: string) => void;
  onRemove: () => void;
  busy: boolean;
}

/**
 * One note as both of the web app's drawers draw it: author, kind, pinned, when, the words, and what it is about.
 * Pin is anyone's; Edit and Delete are its author's or an admin's. Delete asks first, a stray click being too easy,
 * and is still taken back with the toast's Undo.
 */
export function NoteCard({
  note,
  actions,
  teammates = null,
}: {
  note: NoteRecord;
  actions?: NoteCardActions;
  /** The team an edit may mention: the position the note is about; null for a note about no position. */
  teammates?: readonly TeammateOption[] | null;
}) {
  const [editing, setEditing] = useState(false);
  const [confirmingRemove, setConfirmingRemove] = useState(false);
  const [draft, setDraft] = useState(note.body);
  const revised = draft.trim();
  const revisedTooLong = revised.length > FIELD_LIMITS.noteBody;
  const canSave = revised.length > 0 && !revisedTooLong;
  const authorName = note.authorName ?? "Someone";

  const startEditing = () => {
    setDraft(note.body);
    setEditing(true);
  };
  const saveRevision = () => {
    if (!actions || !canSave) return;
    // An untouched draft is still the stored body; sent back as Markdown, a plain note's "*" and "1." would format.
    if (draft !== note.body) actions.onRevise(revised);
    setEditing(false);
  };

  return (
    <li
      className={cn(
        "rounded-[8px] border px-3 py-2.5",
        note.pinned ? "border-u-accent/60 bg-u-raised" : "border-u-border",
      )}
    >
      <div className="flex items-center gap-2">
        <Avatar id={note.authorUserId} name={authorName} src={note.authorAvatarUrl} size="xs" />
        <span className="min-w-0 truncate text-[12.5px] font-semibold text-u-text">{authorName}</span>
        <span className="flex flex-none items-center gap-1 text-u-text3">
          <NoteKindMark kind={note.kind} size={12} />
          <span className="rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.06em]">
            {noteKindLabel(note.kind)}
          </span>
        </span>
        {note.pinned && (
          <span className="flex flex-none items-center gap-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-accent">
            <Icon d={ICONS.pin} size={10} />
            Pinned
          </span>
        )}
        <time
          dateTime={note.createdAt}
          title={new Date(note.createdAt).toLocaleString()}
          className="ms-auto flex-none font-mono text-[11px] text-u-text3"
        >
          {shortWhen(note.createdAt)}
        </time>
      </div>
      {editing && actions ? (
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
            footer={
              <>
                {revisedTooLong && (
                  <p role="alert" className="mb-2 text-meta text-u-offlimits">
                    {NOTE_TOO_LONG}
                  </p>
                )}
                <div className="flex justify-end gap-2">
                  <Button type="button" variant="secondary" size="xs" onClick={() => setEditing(false)}>
                    Cancel
                  </Button>
                  <Button type="button" size="xs" disabled={!canSave} loading={actions.busy} onClick={saveRevision}>
                    Save
                  </Button>
                </div>
              </>
            }
          />
        </div>
      ) : (
        <ClampedNoteText body={note.body} format={note.format} className="mt-1.5 text-[13px]/[1.55] text-u-text2" />
      )}
      {confirmingRemove && actions ? (
        <div role="alertdialog" aria-label="Delete this note?" className="mt-2 flex items-center gap-2 rounded-u-control bg-u-offlimits-tint px-2.5 py-1.5">
          <span className="flex-1 text-note text-u-offlimits">Delete this note for everyone?</span>
          <Button type="button" variant="secondary" size="xs" onClick={() => setConfirmingRemove(false)}>
            Keep
          </Button>
          <Button
            type="button"
            size="xs"
            className="bg-u-offlimits hover:enabled:bg-u-offlimits"
            onClick={() => {
              setConfirmingRemove(false);
              actions.onRemove();
            }}
          >
            Delete
          </Button>
        </div>
      ) : (
        <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-u-text3">
          {note.projectTitle ? (
            <span className="min-w-0 truncate">About {note.projectTitle}</span>
          ) : (
            note.projectId === null && <span>All positions</span>
          )}
          {note.editedAt && (
            <span title={new Date(note.editedAt).toLocaleString()}>Edited by {note.editedByName ?? "someone"}</span>
          )}
          {actions && (
            <span className="-my-1 ms-auto flex items-center gap-0.5">
              <NoteActionButton
                label={note.pinned ? "Unpin" : "Pin"}
                icon={ICONS.pin}
                active={note.pinned}
                onClick={actions.onPin}
                disabled={actions.busy}
              />
              {note.editable && !editing && (
                <NoteActionButton label="Edit" icon={ICONS.pencil} onClick={startEditing} disabled={actions.busy} />
              )}
              {note.editable && (
                <NoteActionButton
                  label="Delete"
                  icon={ICONS.trash}
                  danger
                  onClick={() => setConfirmingRemove(true)}
                  disabled={actions.busy}
                />
              )}
            </span>
          )}
        </div>
      )}
    </li>
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
        "grid size-7 place-items-center rounded-u-control text-u-text3 transition hover:bg-u-raised disabled:opacity-50",
        danger ? "hover:text-u-offlimits" : "hover:text-u-text",
        active && "text-u-accent hover:text-u-accent",
      )}
    >
      <Icon d={icon} size={13} />
    </button>
  );
}
