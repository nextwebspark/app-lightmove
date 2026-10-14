import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { moveItem } from "../../../lib/moveItem";
import { useSortableSensors, VERTICAL_LIST_MODIFIERS } from "../../../lib/sortableSensors";
import * as tasksApi from "../../tasks/api/tasksApi";
import type { TaskPreset, TaskPresetChange, TaskPresetDraft } from "../../tasks/api/types";
import {
  RetiredTaskPresetRow,
  TaskPresetEditor,
  TaskPresetRow,
  type PresetEditorFocus,
} from "../components/TaskPresetRow";
import { inPresetOrder, openSuggestions, timesLabel } from "../lib/taskPresetView";

const NEW_PRESET: TaskPresetDraft = { label: "", kind: "todo", dueOffsetDays: 1 };

/** What the one inline editor is open on: a preset by id, or a new one. */
interface OpenEditor {
  presetId: string | null;
  focus: PresetEditorFocus;
}

/**
 * Settings → Task presets (`Settings.dc.html`, `task-presets`): the workspace's catalog a next action is
 * picked from. Admin-only, as every write behind it is; a retired preset stays on the tasks that use it.
 */
export function SettingsTaskPresetsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const sensors = useSortableSensors();
  const [editor, setEditor] = useState<OpenEditor | null>(null);
  const [draft, setDraft] = useState<TaskPresetDraft>(NEW_PRESET);
  const [retiredOpen, setRetiredOpen] = useState(false);

  const presets = useQuery({
    queryKey: tasksApi.TASK_PRESETS_KEY,
    queryFn: ({ signal }) => tasksApi.getTaskPresets(signal),
  });
  const suggestions = useQuery({
    queryKey: tasksApi.TASK_PRESET_SUGGESTIONS_KEY,
    queryFn: ({ signal }) => tasksApi.getTaskPresetSuggestions(signal),
  });

  // The suggestions key sits under the presets key, so this reads both again.
  const refresh = () => void queryClient.invalidateQueries({ queryKey: tasksApi.TASK_PRESETS_KEY });
  const refuse = (error: unknown) => toast.error(messageFor(error));

  const adding = useMutation({
    mutationFn: (preset: TaskPresetDraft) => tasksApi.createTaskPreset(preset),
    onSuccess: () => {
      setEditor(null);
      toast("Preset added for the whole team");
    },
    onError: refuse,
    onSettled: refresh,
  });

  const changing = useMutation({
    mutationFn: ({ presetId, change }: { presetId: string; change: TaskPresetChange }) =>
      tasksApi.updateTaskPreset(presetId, change),
    onSuccess: (_, { change }) => {
      if (change.retired === true) toast("Retired — tasks that use it keep it");
      else if (change.retired === false) toast("Preset offered again");
      else {
        setEditor(null);
        toast("Preset saved");
      }
    },
    onError: refuse,
    onSettled: refresh,
  });

  const reordering = useMutation({
    mutationFn: (liveIds: string[]) => tasksApi.reorderTaskPresets(liveIds),
    onSuccess: (saved) => queryClient.setQueryData(tasksApi.TASK_PRESETS_KEY, saved),
    onError: (error) => {
      refuse(error);
      refresh();
    },
  });

  const all = [...(presets.data ?? [])].sort((a, b) => a.position - b.position);
  const live = all.filter((preset) => !preset.retired);
  const retired = all.filter((preset) => preset.retired);
  const liveIds = live.map((preset) => preset.id);
  const offered = openSuggestions(suggestions.data ?? [], all);

  const openEditor = (preset: TaskPreset | null, focus: PresetEditorFocus, start?: TaskPresetDraft) => {
    setDraft(start ?? (preset ? { label: preset.label, kind: preset.kind, dueOffsetDays: preset.dueOffsetDays } : NEW_PRESET));
    setEditor({ presetId: preset?.id ?? null, focus });
  };

  const handleSave = () => {
    const label = draft.label.trim();
    if (!editor || !label) return;
    if (editor.presetId === null) {
      adding.mutate({ ...draft, label });
      return;
    }
    const preset = all.find((candidate) => candidate.id === editor.presetId);
    const change = preset ? changeOf(preset, { ...draft, label }) : null;
    if (!preset || !change) {
      setEditor(null);
      return;
    }
    changing.mutate({ presetId: preset.id, change });
  };

  const move = (from: number, to: number) => {
    if (from < 0 || to < 0 || to >= liveIds.length || from === to) return;
    const ids = moveItem(liveIds, from, to);
    void queryClient.cancelQueries({ queryKey: tasksApi.TASK_PRESETS_KEY, exact: true });
    queryClient.setQueryData<TaskPreset[]>(tasksApi.TASK_PRESETS_KEY, (current) => current && inPresetOrder(current, ids));
    reordering.mutate(ids);
  };

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    if (over) move(liveIds.indexOf(String(active.id)), liveIds.indexOf(String(over.id)));
  };

  const editorFor = (placeholder?: string) =>
    editor && (
      <TaskPresetEditor
        draft={draft}
        focus={editor.focus}
        saving={adding.isPending || (changing.isPending && changing.variables.change.retired === undefined)}
        placeholder={placeholder}
        onChange={setDraft}
        onSave={handleSave}
        onCancel={() => setEditor(null)}
      />
    );

  return (
    <>
      <PageHeader
        title="Task presets"
        subtitle="Presets your team picks from when setting a next action. Anyone can still type their own."
        action={
          <Button size="sm" disabled={editor !== null || !presets.isSuccess} onClick={() => openEditor(null, "label")}>
            <Icon d={ICONS.plus} size={14} />
            Add preset
          </Button>
        }
      />

      {presets.isError ? (
        <p className="text-note text-u-text3">{messageFor(presets.error)}</p>
      ) : presets.isPending ? (
        <CardsSkeleton />
      ) : (
        <>
          <div className="rounded-u-card border border-u-border bg-u-raised px-4">
            <div className="hidden items-center gap-3 border-b border-u-border py-2.5 font-mono type-micro-label text-u-text3 sm:flex">
              <span className="w-3.5" />
              <span className="min-w-0 flex-1">Preset</span>
              <span className="w-[104px]">Type</span>
              <span className="w-[84px]">Default due</span>
              <span className="w-[110px] text-right">Usage</span>
              <span className="w-[30px]" />
            </div>
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              modifiers={VERTICAL_LIST_MODIFIERS}
              onDragEnd={handleDragEnd}
            >
              <SortableContext items={liveIds} strategy={verticalListSortingStrategy}>
                <ul aria-label="Presets" className="divide-y divide-u-border">
                  {live.map((preset, index) =>
                    editor?.presetId === preset.id ? (
                      <li key={preset.id}>{editorFor()}</li>
                    ) : (
                      <TaskPresetRow
                        key={preset.id}
                        preset={preset}
                        onStep={(step) => move(index, index + step)}
                        onEdit={(focus) => openEditor(preset, focus)}
                        onRetire={() => changing.mutate({ presetId: preset.id, change: { retired: true } })}
                      />
                    ),
                  )}
                </ul>
              </SortableContext>
            </DndContext>
            {live.length === 0 && editor?.presetId !== null && (
              <p className="py-3 font-mono text-note text-u-text3">No presets are offered. Add one, or restore a retired one.</p>
            )}
            {editor?.presetId === null && (
              <div className={cn(live.length > 0 && "border-t border-u-border")}>
                {editorFor("New preset, e.g. Check notice period")}
              </div>
            )}
          </div>

          {retired.length > 0 && (
            <div className="mt-3 overflow-hidden rounded-u-card border border-u-border bg-u-raised">
              <button
                type="button"
                onClick={() => setRetiredOpen((open) => !open)}
                aria-expanded={retiredOpen}
                className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left transition hover:bg-u-surface"
              >
                <Icon
                  d={ICONS.chevronDown}
                  size={15}
                  className={cn("flex-none text-u-text2 transition-transform", !retiredOpen && "-rotate-90")}
                />
                <span className="text-body font-semibold text-u-text">Retired</span>
                <span className="font-mono text-note text-u-text3">{retired.length}</span>
              </button>
              {retiredOpen && (
                <ul aria-label="Retired presets" className="divide-y divide-u-border border-t border-u-border px-4">
                  {retired.map((preset) => (
                    <RetiredTaskPresetRow
                      key={preset.id}
                      preset={preset}
                      busy={changing.isPending}
                      onRestore={() => changing.mutate({ presetId: preset.id, change: { retired: false } })}
                    />
                  ))}
                </ul>
              )}
            </div>
          )}

          <p className="mx-0.5 mt-3 max-w-[620px] font-mono text-meta text-u-text3">
            A retired preset stays on the tasks that already use it, but is no longer offered. Changing a preset never
            changes a task already set from it.
          </p>

          {offered.length > 0 && (
            <section
              aria-label="Most typed, not a preset"
              className="mt-5 rounded-u-card border border-dashed border-u-border-strong bg-u-raised px-[18px] py-4"
            >
              <div className="text-body font-semibold text-u-text">Most typed, not a preset</div>
              <div className="mt-0.5 font-mono text-meta text-u-text3">Titles your team typed by hand in the last 90 days.</div>
              <ul className="mt-2 divide-y divide-u-border">
                {offered.map((suggestion) => (
                  <li key={suggestion.label} className="flex flex-wrap items-center gap-3 py-2">
                    <span className="min-w-0 flex-1 text-body text-u-text">{suggestion.label}</span>
                    <span className="font-mono text-note text-u-text3">typed {timesLabel(suggestion.count)}</span>
                    <Button
                      variant="secondary"
                      size="xs"
                      disabled={editor !== null}
                      aria-label={`Make a preset of ${suggestion.label}`}
                      onClick={() => openEditor(null, "kind", { ...NEW_PRESET, label: suggestion.label })}
                    >
                      <Icon d={ICONS.plus} size={12} />
                      Make a preset
                    </Button>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </>
  );
}

/** Only what the editor changed, with a cleared default due said as the server wants it said. */
function changeOf(preset: TaskPreset, draft: TaskPresetDraft): TaskPresetChange | null {
  const change: TaskPresetChange = {};
  if (draft.label !== preset.label) change.label = draft.label;
  if (draft.kind !== preset.kind) change.kind = draft.kind;
  if (draft.dueOffsetDays !== preset.dueOffsetDays) {
    if (draft.dueOffsetDays === null) change.clearDueOffset = true;
    else change.dueOffsetDays = draft.dueOffsetDays;
  }
  return Object.keys(change).length > 0 ? change : null;
}
