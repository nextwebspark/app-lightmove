import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui/Avatar";
import { Button, TextArea } from "../../../components/ui";
import { useRadioGroupKeys } from "../../../components/ui/useRadioGroupKeys";
import { cn } from "../../../lib/cn";
import type { PersonNote, PersonNoteKind } from "../api/types";
import { shortWhen } from "../lib/candidateActivity";
import { NOTE_KINDS, noteKindLabel } from "../lib/noteKinds";

/** The composer's kind chips: a radio group with one tab stop, the arrows moving the choice. */
export function NoteKindPicker({ value, onChange }: { value: PersonNoteKind; onChange: (kind: PersonNoteKind) => void }) {
  const keys = useRadioGroupKeys(
    NOTE_KINDS.map((kind) => kind.value),
    value,
    onChange,
  );
  return (
    <div
      ref={keys.ref}
      role="radiogroup"
      aria-label="Kind of note"
      onKeyDown={keys.onKeyDown}
      className="mb-2 flex flex-wrap gap-1.5"
    >
      {NOTE_KINDS.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          tabIndex={value === option.value ? 0 : -1}
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

/** What a card may do to its note; a card drawn without them only reads. */
export interface NoteCardActions {
  onPin: () => void;
  onRevise: (body: string) => void;
  onRemove: () => void;
  busy: boolean;
}

/**
 * One note as both drawers draw it: author, kind, pinned, when, the words, and what it is about. With
 * actions it offers Pin to anyone and Edit and Delete to whoever may change it; Delete asks first,
 * because a removed note leaves no copy of its words anywhere.
 */
export function NoteCard({ note, actions }: { note: PersonNote; actions?: NoteCardActions }) {
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [draft, setDraft] = useState(note.body);

  return (
    <li className={cn("rounded-[8px] border px-3 py-2.5", note.pinned ? "border-u-accent/60 bg-u-raised" : "border-u-border")}>
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
          className="mt-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (draft.trim()) {
              actions.onRevise(draft.trim());
              setEditing(false);
            }
          }}
        >
          <TextArea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            rows={3}
            maxLength={4000}
            aria-label="Edit note"
            className="font-sans text-[13px]/[1.55]"
          />
          <div className="mt-2 flex justify-end gap-2">
            <Button type="button" variant="secondary" className="px-3 py-1 text-[12px]" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button type="submit" className="px-3 py-1 text-[12px]" disabled={!draft.trim()} loading={actions.busy}>
              Save
            </Button>
          </div>
        </form>
      ) : (
        <p className="mt-1.5 whitespace-pre-wrap text-[13px]/[1.55] text-u-text2">{note.body}</p>
      )}
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-[11px] text-u-text3">
        {note.projectTitle && <span>About {note.projectTitle}</span>}
        {note.editedAt && (
          <span title={new Date(note.editedAt).toLocaleString()}>Edited by {note.editedByName ?? "someone"}</span>
        )}
        {actions &&
          (confirming ? (
            <span className="ms-auto flex items-center gap-2" role="group" aria-label="Delete this note?">
              <span className="text-u-text2">Delete this note?</span>
              <button
                type="button"
                onClick={() => {
                  setConfirming(false);
                  actions.onRemove();
                }}
                className="font-semibold text-u-offlimits hover:underline"
              >
                Yes, delete
              </button>
              <button type="button" onClick={() => setConfirming(false)} className="hover:text-u-text">
                No
              </button>
            </span>
          ) : (
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
                <button type="button" onClick={() => setConfirming(true)} className="hover:text-u-offlimits">
                  Delete
                </button>
              )}
            </span>
          ))}
      </div>
    </li>
  );
}
