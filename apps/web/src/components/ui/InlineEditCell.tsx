import { useState, type KeyboardEvent } from "react";
import { useSubmitShortcut } from "../../lib/useSubmitShortcut";
import { Input } from "./index";
import { DataGridCell } from "./DataGrid";

/**
 * A grid cell that reads first and edits second, in place — click to open, blur or Ctrl/⌘-Enter to
 * save, Escape to cancel. A single-line input rather than the drawer's multi-line textarea: rows in
 * this grid are a fixed 52px, and the truncated text a reader clicked was already one line.
 */
export function InlineEditCell({
  value,
  editable,
  onSave,
  placeholder,
}: {
  value: string | null;
  editable: boolean;
  /** Resolves with what the server now holds; rejecting leaves the cell open on the unsaved draft. */
  onSave: (next: string) => Promise<unknown>;
  placeholder?: string;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);

  const open = () => {
    if (!editable) return;
    setDraft(value ?? "");
    setEditing(true);
  };

  const commit = () => {
    const next = draft.trim();
    if (next === (value ?? "")) {
      setEditing(false);
      return;
    }
    setSaving(true);
    onSave(next)
      .then(() => setEditing(false))
      .finally(() => setSaving(false));
  };

  const handleShortcut = useSubmitShortcut(commit);

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      setEditing(false);
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
        className="flex w-full min-w-0 rounded-[4px] text-start transition hover:bg-panel2"
      >
        <DataGridCell value={value} muted />
      </button>
    ) : (
      <DataGridCell value={value} muted />
    );
  }

  return (
    <Input
      autoFocus
      value={draft}
      disabled={saving}
      onChange={(event) => setDraft(event.target.value)}
      onKeyDown={handleKeyDown}
      onBlur={commit}
      placeholder={placeholder}
      className="h-8 px-2 py-1 text-[13px]"
    />
  );
}
