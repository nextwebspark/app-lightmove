import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { Button } from "../../../components/ui";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import type { PersonNote, PersonNoteKind } from "../api/types";
import { shortWhen } from "../lib/candidateActivity";
import { NOTE_KINDS, noteKindLabel } from "../lib/noteKinds";
import { NoteEditor } from "../../notes/components/NoteEditor";
import { NoteText } from "../../notes/components/NoteText";
import { NOTE_MAX_STORED, NOTE_TOO_LONG } from "../../tasks/lib/mentions";
import type { TeammateOption } from "../../tasks/lib/team";

/** Who reads a note written inside a position: that position's team, or every position's. */
export type NoteScope = "position" | "everywhere";

export function NoteKindPicker({ value, onChange }: { value: PersonNoteKind; onChange: (kind: PersonNoteKind) => void }) {
  return (
    <NoteChoiceChips label="Kind of note" options={NOTE_KINDS} value={value} onChange={onChange} className="mb-2" />
  );
}

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

/** The composer's chips: a radio group with one tab stop, the arrows moving the choice. */
function NoteChoiceChips<TValue extends string>({
  label,
  options,
  value,
  onChange,
  className,
}: {
  label: string;
  options: readonly { value: TValue; label: string; title?: string }[];
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
          className={cn(
            "rounded-full border px-2.5 py-0.5 font-mono text-[11px] transition",
            value === option.value
              ? "border-u-accent bg-u-accent-tint text-u-accent"
              : "border-u-border text-u-text3 hover:text-u-text2",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
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

/** What a card may do to its note; a card drawn without them only reads. */
export interface NoteCardActions {
  onPin: () => void;
  onRevise: (body: string) => void;
  onRemove: () => void;
  busy: boolean;
}

/**
 * One note as both drawers draw it: author, kind, pinned, when, the words, and what it is about. With
 * actions it offers Pin to anyone and Edit and Delete to whoever may change it; Delete is taken back with
 * the toast's Undo, not asked about first.
 */
export function NoteCard({
  note,
  actions,
  teammates = null,
  flashAt = null,
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
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(note.body);
  const revised = draft.trim();
  const revisedTooLong = revised.length > NOTE_MAX_STORED;
  const canSave = revised.length > 0 && !revisedTooLong;
  const saveRevision = () => {
    if (!actions || !canSave) return;
    actions.onRevise(revised);
    setEditing(false);
  };

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
      <div className="flex items-center gap-2">
        <Avatar id={note.authorUserId} name={note.authorName ?? "Someone"} src={note.authorAvatarUrl} size="sm" />
        <span className="text-[12.5px] font-semibold">{note.authorName ?? "Someone"}</span>
        <span className="rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.06em] text-u-text3">
          {noteKindLabel(note.kind)}
        </span>
        {note.pinned && (
          <span className="flex items-center gap-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-u-accent">
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
        <form
          className="mt-2 rounded-u-control border border-u-border p-2"
          onSubmit={(event) => {
            event.preventDefault();
            saveRevision();
          }}
        >
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
          />
          {revisedTooLong && (
            <p role="alert" className="mt-1.5 text-meta text-u-offlimits">
              {NOTE_TOO_LONG}
            </p>
          )}
          <div className="mt-2 flex justify-end gap-2">
            <Button type="button" variant="secondary" className="px-3 py-1 text-[12px]" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button
              type="submit"
              className="px-3 py-1 text-[12px]"
              disabled={!canSave}
              loading={actions.busy}
            >
              Save
            </Button>
          </div>
        </form>
      ) : (
        <NoteText body={note.body} format={note.format} className="mt-1.5 text-[13px]/[1.55] text-u-text2" />
      )}
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-u-text3">
        {note.projectTitle ? (
          <span>About {note.projectTitle}</span>
        ) : (
          note.projectId === null && <span>All positions</span>
        )}
        {note.editedAt && (
          <span title={new Date(note.editedAt).toLocaleString()}>Edited by {note.editedByName ?? "someone"}</span>
        )}
        {actions && (
          <span className="ms-auto flex gap-3">
            <button type="button" onClick={actions.onPin} disabled={actions.busy} className="hover:text-u-text">
              {note.pinned ? "Unpin" : "Pin"}
            </button>
            {note.editable && !editing && (
              <button
                type="button"
                onClick={() => {
                  setDraft(note.body);
                  setEditing(true);
                }}
                className="hover:text-u-text"
              >
                Edit
              </button>
            )}
            {note.editable && (
              <button
                type="button"
                onClick={actions.onRemove}
                disabled={actions.busy}
                className="hover:text-u-offlimits"
              >
                Delete
              </button>
            )}
          </span>
        )}
      </div>
    </li>
  );
}
