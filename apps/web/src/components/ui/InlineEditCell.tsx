import { useState, type KeyboardEvent } from "react";
import { useSubmitShortcut } from "../../lib/useSubmitShortcut";
import { Icon, ICONS } from "../layout/Icon";
import { Input } from "./index";
import { DataGridCell } from "./DataGrid";

/**
 * A grid cell that reads first and edits second, in place — click to open, Save/Cancel (or blur/
 * Ctrl-⌘-Enter/Escape, unchanged) to close. A single-line input rather than the drawer's multi-line
 * textarea: rows in this grid are a fixed 52px, and the truncated text a reader clicked was already
 * one line.
 *
 * <p>Save and Cancel are explicit buttons, not just the blur/Escape they duplicate — this is the only
 * place in the grid a value can be cleared to empty, and a silent commit-on-blur reads as "there is no
 * way to delete this" rather than as "already done".
 */
export function InlineEditCell({
  value,
  editable,
  onSave,
  placeholder,
  "aria-label": ariaLabel,
}: {
  value: string | null;
  editable: boolean;
  /** Resolves with what the server now holds; rejecting leaves the cell open on the unsaved draft. */
  onSave: (next: string) => Promise<unknown>;
  placeholder?: string;
  /** Names the field for a screen reader, and disambiguates it from the grid's own header filters. */
  "aria-label"?: string;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);

  const dirty = draft.trim() !== (value ?? "").trim();

  const open = () => {
    if (!editable) return;
    setDraft(value ?? "");
    setEditing(true);
  };

  const cancel = () => {
    setDraft(value ?? "");
    setEditing(false);
  };

  const commit = () => {
    if (!dirty) {
      setEditing(false);
      return;
    }
    setSaving(true);
    onSave(draft.trim())
      .then(() => setEditing(false))
      .catch(() => {
        // Left open on the unsaved draft, as the prop's own doc comment promises — the caller's
        // mutation already surfaced the error toast.
      })
      .finally(() => setSaving(false));
  };

  const handleShortcut = useSubmitShortcut(commit);

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      if (saving) return;
      cancel();
      return;
    }
    handleShortcut(event);
  };

  if (!editing) {
    return editable ? (
      <button
        type="button"
        onClick={open}
        title="Click to edit"
        className="flex w-full min-w-0 rounded-[4px] text-start transition hover:bg-u-raised"
      >
        <DataGridCell value={value} muted />
      </button>
    ) : (
      <DataGridCell value={value} muted />
    );
  }

  return (
    <div className="flex w-full min-w-0 items-center gap-1">
      <Input
        autoFocus
        value={draft}
        disabled={saving}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={commit}
        placeholder={placeholder}
        aria-label={ariaLabel}
        className="h-8 min-w-0 flex-1 px-2 py-1 text-[13px]"
      />
      {/* Clicking these must not blur the input first — a blur would fire `commit` from `onBlur`
          a beat before the click handler below it runs, saving twice or racing the two calls. */}
      <button
        type="button"
        title="Save"
        aria-label="Save"
        disabled={saving || !dirty}
        onMouseDown={(event) => event.preventDefault()}
        onClick={commit}
        className="grid size-6 shrink-0 place-items-center rounded-[4px] text-u-text3 transition hover:bg-u-raised hover:text-u-direct disabled:pointer-events-none disabled:opacity-40"
      >
        <Icon d={ICONS.check} size={14} />
      </button>
      <button
        type="button"
        title="Cancel"
        aria-label="Cancel"
        disabled={saving}
        onMouseDown={(event) => event.preventDefault()}
        onClick={cancel}
        className="grid size-6 shrink-0 place-items-center rounded-[4px] text-u-text3 transition hover:bg-u-raised hover:text-u-offlimits disabled:pointer-events-none disabled:opacity-40"
      >
        <Icon d={ICONS.close} size={14} />
      </button>
    </div>
  );
}
