import { lazy, Suspense, type ReactNode } from "react";
import { cn } from "../../../lib/cn";
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
  /**
   * The composer is a dialog already: it fills it — the header and footer pinned, only the words scrolling — and
   * Expand grows that dialog through `onExpandChange` rather than opening a second, larger one over it.
   */
  fillsDialog?: boolean;
  onExpandChange?: (expanded: boolean) => void;
}

/** The note editor, loaded on first use so a page that never writes a note never downloads it. */
export function NoteEditor(props: NoteEditorProps) {
  if (props.fillsDialog) {
    // Header and footer stay outside the lazy boundary: a Subject typed while the editor loads keeps its focus.
    return (
      <div className="flex min-h-0 flex-1 flex-col">
        {props.header !== undefined && <div className="flex-none">{props.header}</div>}
        <div className={cn("flex min-h-0 flex-1 flex-col", props.header !== undefined && "mt-3")}>
          <Suspense fallback={<div aria-busy="true" className="min-h-40 flex-1" />}>
            <RichNoteEditor {...props} header={undefined} footer={undefined} />
          </Suspense>
        </div>
        {props.footer && <div className="mt-3 flex-none">{props.footer}</div>}
      </div>
    );
  }
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
