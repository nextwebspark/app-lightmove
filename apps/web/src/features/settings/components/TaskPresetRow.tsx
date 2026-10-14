import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { ReactNode } from "react";
import { Icon } from "../../../components/layout/Icon";
import { useDropdownMenu } from "../../../components/layout/useDropdownMenu";
import { Button, Input, Select } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import type { TaskKind, TaskPreset, TaskPresetDraft } from "../../tasks/api/types";
import {
  dueOffsetLabel,
  dueOffsetsFor,
  retiredLabel,
  TASK_KIND_CHIPS,
  TASK_KINDS,
  usageLabel,
} from "../lib/taskPresetView";
import { DragHandle } from "./PipelineRowControls";

const MORE_VERTICAL = "M12 5h.01M12 12h.01M12 19h.01";

/** Which control the inline editor opens on: the menu's Rename, Change type or Change default due. */
export type PresetEditorFocus = "label" | "kind" | "due";

/** The task kind as a coloured chip with its glyph. */
export function TaskKindChip({ kind }: { kind: TaskKind }) {
  const chip = TASK_KIND_CHIPS[kind];
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2 py-0.5 text-meta font-medium",
        chip.className,
      )}
    >
      <Icon d={chip.glyph} size={12} />
      {chip.label}
    </span>
  );
}

/** A live preset: its handle, what it says, and the ⋮ menu that opens the editor or retires it. */
export function TaskPresetRow({
  preset,
  onStep,
  onEdit,
  onRetire,
}: {
  preset: TaskPreset;
  onStep: (step: number) => void;
  onEdit: (focus: PresetEditorFocus) => void;
  onRetire: () => void;
}) {
  const sortable = useSortable({ id: preset.id });
  const menu = useDropdownMenu();

  const choose = (act: () => void) => {
    menu.close();
    act();
  };

  return (
    <li
      ref={sortable.setNodeRef}
      style={{ transform: CSS.Translate.toString(sortable.transform), transition: sortable.transition }}
      aria-label={preset.label}
      className={cn(
        "flex flex-wrap items-center gap-3 py-2.5",
        sortable.isDragging && "relative z-10 bg-u-raised opacity-60",
      )}
    >
      <DragHandle label={`Reorder ${preset.label}`} sortable={sortable} onStep={onStep} />
      <span className="min-w-0 flex-1 truncate text-body font-medium text-u-text">{preset.label}</span>
      <span className="w-[104px] flex-none">
        <TaskKindChip kind={preset.kind} />
      </span>
      <span className="w-[84px] flex-none font-mono text-note text-u-text2">{dueOffsetLabel(preset.dueOffsetDays)}</span>
      <span className="w-[110px] flex-none text-right font-mono text-note text-u-text3">{usageLabel(preset.usage)}</span>
      <div ref={menu.rootRef} className="relative flex w-[30px] flex-none justify-end">
        <button
          ref={menu.triggerRef}
          type="button"
          onClick={menu.toggle}
          {...menu.triggerProps}
          aria-label={`Actions for ${preset.label}`}
          className="grid place-items-center rounded-u-control px-1.5 py-1 text-u-text3 transition hover:bg-u-surface hover:text-u-text aria-expanded:bg-u-surface"
        >
          <Icon d={MORE_VERTICAL} size={14} />
        </button>
        {menu.open && (
          <div
            ref={menu.menuRef}
            {...menu.menuProps}
            aria-label={`Actions for ${preset.label}`}
            className="absolute right-0 top-[30px] z-[80] w-[190px] rounded-u-card border border-u-border-strong bg-u-surface p-1.5 shadow-u-e3"
          >
            <PresetMenuItem onClick={() => choose(() => onEdit("label"))}>Rename</PresetMenuItem>
            <PresetMenuItem onClick={() => choose(() => onEdit("kind"))}>Change type</PresetMenuItem>
            <PresetMenuItem onClick={() => choose(() => onEdit("due"))}>Change default due</PresetMenuItem>
            <div role="separator" className="mx-1 my-1 h-px bg-u-border" />
            <PresetMenuItem title="Stop offering this preset; tasks keep it" onClick={() => choose(onRetire)}>
              Retire
            </PresetMenuItem>
          </div>
        )}
      </div>
    </li>
  );
}

/** A retired preset in its fold: what it was, and Restore. */
export function RetiredTaskPresetRow({
  preset,
  busy,
  onRestore,
}: {
  preset: TaskPreset;
  busy: boolean;
  onRestore: () => void;
}) {
  return (
    <li aria-label={preset.label} className="flex flex-wrap items-center gap-3 py-2.5">
      <span className="w-3.5 flex-none" />
      <span className="min-w-0 flex-1 truncate text-body font-medium text-u-text2">{preset.label}</span>
      <span className="w-[104px] flex-none">
        <TaskKindChip kind={preset.kind} />
      </span>
      <span className="font-mono text-note text-u-text3">
        {retiredLabel(preset)}
      </span>
      <Button
        variant="ghost"
        size="xs"
        className="ms-auto"
        title="Offer this preset again"
        disabled={busy}
        onClick={onRestore}
      >
        Restore
      </Button>
    </li>
  );
}

/** The one inline editor, for adding a preset and for every change the ⋮ menu offers. */
export function TaskPresetEditor({
  draft,
  focus,
  saving,
  placeholder,
  onChange,
  onSave,
  onCancel,
}: {
  draft: TaskPresetDraft;
  focus: PresetEditorFocus;
  saving: boolean;
  placeholder?: string;
  onChange: (draft: TaskPresetDraft) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  return (
    <form
      aria-label={placeholder ? "New preset" : "Edit preset"}
      className="flex flex-wrap items-center gap-3 py-2.5"
      onSubmit={(event) => {
        event.preventDefault();
        if (draft.label.trim()) onSave();
      }}
      onKeyDown={(event) => {
        if (event.key === "Escape") onCancel();
      }}
    >
      <span className="w-3.5 flex-none" />
      <Input
        value={draft.label}
        onChange={(event) => onChange({ ...draft, label: event.target.value })}
        placeholder={placeholder}
        aria-label="Preset name"
        maxLength={200}
        autoFocus={focus === "label"}
        className="min-w-[180px] flex-1 border-u-accent bg-u-surface px-2.5 py-1.5"
      />
      <Select
        value={draft.kind}
        onChange={(event) => onChange({ ...draft, kind: event.target.value as TaskKind })}
        aria-label="Type"
        density="compact"
        autoFocus={focus === "kind"}
        className="w-[120px] flex-none bg-u-surface"
      >
        {TASK_KINDS.map((kind) => (
          <option key={kind} value={kind}>
            {TASK_KIND_CHIPS[kind].label}
          </option>
        ))}
      </Select>
      <Select
        value={draft.dueOffsetDays === null ? "none" : String(draft.dueOffsetDays)}
        onChange={(event) =>
          onChange({ ...draft, dueOffsetDays: event.target.value === "none" ? null : Number(event.target.value) })
        }
        aria-label="Default due"
        density="compact"
        autoFocus={focus === "due"}
        className="w-[110px] flex-none bg-u-surface"
      >
        {dueOffsetsFor(draft.dueOffsetDays).map((days) => (
          <option key={days ?? "none"} value={days === null ? "none" : String(days)}>
            {dueOffsetLabel(days)}
          </option>
        ))}
      </Select>
      <span className="ms-auto flex gap-1">
        <Button type="submit" variant="ghost" size="xs" className="text-u-accent" disabled={!draft.label.trim()} loading={saving}>
          Save
        </Button>
        <Button type="button" variant="ghost" size="xs" onClick={onCancel}>
          Cancel
        </Button>
      </span>
    </form>
  );
}

function PresetMenuItem({ title, onClick, children }: { title?: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      role="menuitem"
      title={title}
      onClick={onClick}
      className="block w-full rounded-u-control px-2.5 py-1.5 text-left text-body text-u-text2 outline-none transition hover:bg-u-raised hover:text-u-text focus-visible:bg-u-raised focus-visible:text-u-text"
    >
      {children}
    </button>
  );
}
