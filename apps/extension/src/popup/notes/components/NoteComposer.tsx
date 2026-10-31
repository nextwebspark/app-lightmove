import type { NoteKind } from "../../../api/types";
import { FIELD_LIMITS } from "../../../api/fieldLimits";
import { Button } from "../../components/Button";
import { noteKindPlaceholder } from "../lib/noteKinds";
import type { TeammateOption } from "../lib/teammates";
import { NoteEditor } from "./NoteEditor";
import { NOTE_TOO_LONG, NoteKindPicker, NoteScopePicker, type NoteScope } from "./NoteParts";

/**
 * The web app's note composer in the panel: the kind of note, the formatting editor with @mentions of the position's
 * team, and who reads it — the same for a person's note and a company's, which offers neither mentions nor a scope.
 * With `save` it carries its own Save note (a record's next note); without, the capture's button files it.
 */
export function NoteComposer({
  kind,
  onKindChange,
  draft,
  onDraftChange,
  scope,
  onScopeChange,
  teammates,
  positionTitle,
  expandTitle,
  onSubmit,
  save,
}: {
  kind: NoteKind;
  onKindChange: (kind: NoteKind) => void;
  draft: string;
  onDraftChange: (draft: string) => void;
  /** A person's note only: about this position or the person on every position. */
  scope?: NoteScope;
  onScopeChange?: (scope: NoteScope) => void;
  /** Who may be mentioned; null where a note mentions nobody (a company's, or one about every position). */
  teammates: readonly TeammateOption[] | null;
  positionTitle: string | null;
  expandTitle: string;
  /** ⌘/Ctrl+Enter. */
  onSubmit?: () => void;
  save?: { label: string; isSaving: boolean; onSave: () => void };
}) {
  const body = draft.trim();
  const tooLong = body.length > FIELD_LIMITS.noteBody;
  const hasFooter = Boolean(scope && onScopeChange) || Boolean(save);

  return (
    <div>
      <NoteEditor
        value={draft}
        format="markdown"
        onChange={onDraftChange}
        teammates={scope === "everywhere" ? null : teammates}
        ariaLabel="Note"
        placeholder={noteKindPlaceholder(kind)}
        onSubmit={onSubmit}
        canSubmit={!tooLong}
        expandTitle={expandTitle}
        header={<NoteKindPicker value={kind} onChange={onKindChange} />}
        footer={
          (hasFooter || tooLong) && (
            <>
              {tooLong && (
                <p role="alert" className="mb-2 text-meta text-u-offlimits">
                  {NOTE_TOO_LONG}
                </p>
              )}
              <div className="flex flex-wrap items-center gap-2">
                {scope && onScopeChange && (
                  <div className="min-w-0 flex-1">
                    <NoteScopePicker positionTitle={positionTitle} value={scope} onChange={onScopeChange} />
                  </div>
                )}
                {save && (
                  <Button
                    type="button"
                    className="ms-auto"
                    disabled={body.length === 0 || tooLong}
                    loading={save.isSaving}
                    onClick={save.onSave}
                  >
                    {save.label}
                  </Button>
                )}
              </div>
            </>
          )
        }
      />
    </div>
  );
}
