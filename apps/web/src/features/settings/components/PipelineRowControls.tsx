import type { useSortable } from "@dnd-kit/sortable";
import type { KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { PIPELINE_COLOURS, pipelineSwatchClass } from "../../candidates/lib/pipelineStatus";
import { PIPELINE_NAME_LIMIT } from "../lib/pipelineStageRows";
import type { RenameDraft } from "../lib/usePipelineRowEdits";

/** A name being retyped in its row. Its Save button sits elsewhere in the row and submits it by `formId`. */
export function RenameForm({
  formId,
  label,
  rename,
  onSave,
}: {
  formId: string;
  label: string;
  rename: RenameDraft;
  onSave: (name: string) => void;
}) {
  return (
    <form
      id={formId}
      className="contents"
      onSubmit={(event) => {
        event.preventDefault();
        if (rename.draft.trim()) onSave(rename.draft.trim());
      }}
    >
      <Input
        value={rename.draft}
        onChange={(event) => rename.setDraft(event.target.value)}
        onKeyDown={(event) => event.key === "Escape" && rename.end()}
        aria-label={label}
        maxLength={PIPELINE_NAME_LIMIT}
        autoFocus
        className="max-w-[220px] py-1"
      />
    </form>
  );
}

/** Save and Cancel while a name is being retyped; Rename itself is in the row's ⋯. */
export function RenameActions({ formId, rename, saving }: { formId: string; rename: RenameDraft; saving: boolean }) {
  if (!rename.renaming) return null;
  // Keyed apart: patched in place, the pressed Rename became the form's Save and submitted it mid-click.
  return (
    <>
      <Button key="save" type="submit" form={formId} variant="secondary" size="xs" disabled={!rename.draft.trim()} loading={saving}>
        Save
      </Button>
      <Button key="cancel" type="button" variant="ghost" size="xs" onClick={rename.end}>
        Cancel
      </Button>
    </>
  );
}

export function ColourDot({ colour, size }: { colour: string; size: "stage" | "status" }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "flex-none rounded-full",
        size === "stage" ? "size-[11px]" : "size-[9px]",
        pipelineSwatchClass(colour),
      )}
    />
  );
}

/** The ⋯ menu's first line: every pipeline colour, the current one marked. */
export function ColourSwatches({ colour, onChange }: { colour: string; onChange: (colour: string) => void }) {
  return (
    <div role="group" aria-label="Colour" className="flex flex-wrap gap-1.5 px-2 pb-2 pt-1">
      {PIPELINE_COLOURS.map((option) => (
        <button
          key={option.value}
          type="button"
          role="menuitemradio"
          aria-checked={option.value === colour}
          aria-label={option.label}
          title={option.label}
          onClick={() => onChange(option.value)}
          className={cn(
            "size-[18px] rounded-full border-2 transition-transform hover:scale-110",
            option.value === colour ? "border-u-text" : "border-transparent",
            option.swatch,
          )}
        />
      ))}
    </div>
  );
}

/**
 * The only part of a row that drags it. Two keyboard ways, both deliberate: Space picks the row up for the
 * arrows to carry, and an arrow alone moves it one place and saves — once a press, never per key repeat.
 */
export function DragHandle({
  label,
  sortable,
  onStep,
}: {
  label: string;
  sortable: ReturnType<typeof useSortable>;
  onStep: (step: number) => void;
}) {
  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    sortable.listeners?.onKeyDown?.(event);
    if (sortable.isDragging) return;
    const step = event.key === "ArrowUp" ? -1 : event.key === "ArrowDown" ? 1 : 0;
    if (step === 0) return;
    event.preventDefault();
    if (!event.repeat) onStep(step);
  };

  return (
    <button
      type="button"
      {...sortable.attributes}
      {...sortable.listeners}
      aria-label={label}
      title="Drag to reorder, or focus and use the arrow keys"
      onKeyDown={handleKeyDown}
      className="grid w-3.5 flex-none cursor-grab touch-none place-items-center rounded text-u-text3 transition hover:text-u-text2 active:cursor-grabbing"
    >
      <Icon d={ICONS.dragHandle} size={13} />
    </button>
  );
}
