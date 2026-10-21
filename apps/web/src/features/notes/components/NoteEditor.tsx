import { lazy, Suspense, type ReactNode } from "react";
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
  /** Room for a call's worth of words before it scrolls, where the composer is the whole dialog. */
  roomy?: boolean;
  autoFocus?: boolean;
  /** Above the editor wherever it is — the kind of note — so the larger view is the same composer, only larger. */
  header?: ReactNode;
  /** Under the editor wherever it is — who reads it, and the composer's own save — in the larger view its footer. */
  footer?: ReactNode;
}

/** The note editor, loaded on first use so a page that never writes a note never downloads it. */
export function NoteEditor(props: NoteEditorProps) {
  return (
    <Suspense
      fallback={
        <>
          {props.header}
          <div aria-busy="true" className={props.roomy ? "min-h-48" : props.variant === "field" ? "min-h-28" : "min-h-20"} />
          {props.footer && <div className="mt-2">{props.footer}</div>}
        </>
      }
    >
      <RichNoteEditor {...props} />
    </Suspense>
  );
}
