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

/** Rename, or Save while the name is being retyped. */
export function RenameActions({ formId, rename, saving }: { formId: string; rename: RenameDraft; saving: boolean }) {
  // Keyed apart: patched in place, the pressed Rename became the form's Save and submitted it mid-click.
  return rename.renaming ? (
    <Button
      key="save"
      type="submit"
      form={formId}
      variant="secondary"
      size="xs"
      disabled={!rename.draft.trim()}
      loading={saving}
    >
      Save
    </Button>
  ) : (
    <Button key="rename" type="button" variant="ghost" size="xs" onClick={rename.begin}>
      Rename
    </Button>
  );
}

/** A click moves the swatch on to the next colour, the mockup's way of recolouring in place. */
export function ColourDot({
  colour,
  name,
  size,
  editable,
  onChange,
}: {
  colour: string;
  name: string;
  size: "stage" | "status";
  editable: boolean;
  onChange: (colour: string) => void;
}) {
  const index = PIPELINE_COLOURS.findIndex((candidate) => candidate.value === colour);
  const next = PIPELINE_COLOURS[(index + 1) % PIPELINE_COLOURS.length].value;
  const current = PIPELINE_COLOURS[index]?.label;
  return (
    <button
      type="button"
      disabled={!editable}
      title={editable ? "Change colour" : undefined}
      aria-label={current ? `Colour of ${name}: ${current}` : `Colour of ${name}`}
      onClick={() => onChange(next)}
      className={cn(
        "flex-none rounded-full disabled:cursor-default",
        size === "stage" ? "size-[11px]" : "size-[9px]",
        pipelineSwatchClass(colour),
      )}
    />
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
