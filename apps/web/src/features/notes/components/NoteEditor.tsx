import { lazy, Suspense } from "react";
import type { TeammateOption } from "../../tasks/lib/team";
import type { NoteFormat } from "../lib/noteFormat";

const RichNoteEditor = lazy(() => import("./RichNoteEditor"));

export interface NoteEditorProps {
  /** The stored body; the editor writes Markdown back through `onChange`. */
  value: string;
  /** How `value` was stored: a plain note opens as its own lines, nothing in it read as markup. */
  format: NoteFormat;
  onChange: (body: string) => void;
  /** The position's team for @mentions; null where a note mentions nobody (one about no position). */
  teammates: readonly TeammateOption[] | null;
  ariaLabel: string;
  placeholder?: string;
  /** ⌘/Ctrl+Enter, and the larger view's own button. */
  onSubmit?: () => void;
  submitLabel?: string;
  canSubmit?: boolean;
  /** The larger view's title. */
  expandTitle?: string;
  variant?: "note" | "field";
  autoFocus?: boolean;
}

/** The note editor, loaded on first use so a page that never writes a note never downloads it. */
export function NoteEditor(props: NoteEditorProps) {
  return (
    <Suspense
      fallback={<div aria-busy="true" className={props.variant === "field" ? "min-h-28" : "min-h-20"} />}
    >
      <RichNoteEditor {...props} />
    </Suspense>
  );
}
