import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { moveItem } from "../../../lib/moveItem";
import { useSortableSensors, VERTICAL_LIST_MODIFIERS } from "../../../lib/sortableSensors";
import { defaultStatusOf, liveStatuses } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { PipelineCatalog, PipelineStage, PipelineStatus, PipelineStatusChange } from "../../workspace/api/types";
import { usePipelineWrite } from "../../workspace/lib/usePipelineCatalog";
import { inOrderOf, PIPELINE_NAME_LIMIT } from "../lib/pipelineStageRows";
import { ColourDot, DragHandle, RenameForm } from "./PipelineRowControls";

/** One stage's statuses, reordered within the stage alone, and the form that adds one. */
export function PipelineStatusList({ stage, isAdmin }: { stage: PipelineStage; isAdmin: boolean }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const sensors = useSortableSensors();
  const [newStatus, setNewStatus] = useState("");
  const addStatus = usePipelineWrite((label: string) => pipelineApi.createStatus(stage.id, label));
  const reorderStatuses = usePipelineWrite((ids: string[]) => pipelineApi.reorderStatuses(stage.id, ids));
  const statuses = liveStatuses(stage);
  const defaultStatus = defaultStatusOf(stage);
  const statusIds = statuses.map((status) => status.id);

  const moveStatus = (from: number, to: number) => {
    if (from < 0 || to < 0 || to >= statusIds.length || from === to) return;
    const ids = moveItem(statusIds, from, to);
    const previous = queryClient.getQueryData<PipelineCatalog>(pipelineApi.PIPELINE_CATALOG_KEY);
    if (previous) {
      queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, {
        stages: previous.stages.map((candidate) =>
          candidate.id === stage.id ? { ...candidate, statuses: inOrderOf(candidate.statuses, ids) } : candidate,
        ),
      });
    }
    reorderStatuses.mutate(ids, {
      onError: (error) => {
        if (previous) queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, previous);
        toast.error(messageFor(error));
      },
    });
  };

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    if (over) moveStatus(statusIds.indexOf(String(active.id)), statusIds.indexOf(String(over.id)));
  };

  const handleAddStatus = (event: FormEvent) => {
    event.preventDefault();
    const label = newStatus.trim();
    if (!label) return;
    addStatus.mutate(label, {
      onSuccess: () => {
        setNewStatus("");
        toast("Status added");
      },
      onError: (error) => toast.error(messageFor(error)),
    });
  };

  return (
    <>
      {statuses.length === 0 && (
        <p className="px-4 py-3 ps-10 font-mono text-meta text-u-text3">
          No statuses yet. The first one you add becomes the default.
        </p>
      )}
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={VERTICAL_LIST_MODIFIERS}
        onDragEnd={handleDragEnd}
      >
        <ul className="px-4 ps-10">
          <SortableContext items={statusIds} strategy={verticalListSortingStrategy}>
            {statuses.map((status, index) => (
              <StatusRow
                key={status.id}
                status={status}
                isDefault={defaultStatus?.id === status.id}
                isAdmin={isAdmin}
                onStep={(step) => moveStatus(index, index + step)}
              />
            ))}
          </SortableContext>
        </ul>
      </DndContext>

      {isAdmin && (
        <form
          className="flex flex-wrap items-center gap-2 border-t border-u-border px-4 py-2.5 ps-10"
          onSubmit={handleAddStatus}
        >
          <Input
            value={newStatus}
            onChange={(event) => setNewStatus(event.target.value)}
            placeholder={`New status in ${stage.label}`}
            aria-label={`New status in ${stage.label}`}
            maxLength={PIPELINE_NAME_LIMIT}
            className="max-w-[240px] py-1.5"
          />
          <Button type="submit" variant="secondary" size="xs" disabled={!newStatus.trim()} loading={addStatus.isPending}>
            <Icon d={ICONS.plus} size={12} />
            Add status
          </Button>
        </form>
      )}
    </>
  );
}

function StatusRow({
  status,
  isDefault,
  isAdmin,
  onStep,
}: {
  status: PipelineStatus;
  isDefault: boolean;
  isAdmin: boolean;
  onStep: (step: number) => void;
}) {
  const toast = useToast();
  const sortable = useSortable({ id: status.id, disabled: !isAdmin });
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(status.label);
  const update = usePipelineWrite((change: PipelineStatusChange) => pipelineApi.updateStatus(status.id, change));

  const change = (patch: PipelineStatusChange, success?: string) =>
    update.mutate(patch, {
      onSuccess: () => {
        if (patch.label !== undefined) setRenaming(false);
        if (success) toast(success);
      },
      onError: (error) => toast.error(messageFor(error)),
    });

  return (
    <li
      ref={sortable.setNodeRef}
      style={{ transform: CSS.Translate.toString(sortable.transform), transition: sortable.transition }}
      aria-label={status.label}
      className={cn(
        "flex flex-wrap items-center gap-2.5 border-t border-u-border py-2 first:border-t-0",
        sortable.isDragging && "relative z-10 bg-u-surface shadow-u-e3",
      )}
    >
      {isAdmin && <DragHandle label={`Reorder ${status.label}`} sortable={sortable} onStep={onStep} />}
      <ColourDot
        colour={status.colour}
        name={status.label}
        size="status"
        editable={isAdmin}
        onChange={(colour) => change({ colour })}
      />
      {renaming ? (
        <RenameForm
          id={`rename-status-${status.id}`}
          label="Status name"
          draft={draft}
          onDraft={setDraft}
          onCancel={() => setRenaming(false)}
          onSave={() => change({ label: draft.trim() }, "Renamed on every position")}
        />
      ) : (
        <span className="text-note text-u-text">{status.label}</span>
      )}
      {isDefault && (
        <span className="rounded-sm bg-u-accent-tint px-1.5 py-px font-mono text-eyebrow font-medium text-u-accent">
          Default
        </span>
      )}
      {status.system && (
        <span
          title="Built-in status — can be renamed, not retired"
          className="inline-flex items-center gap-1 rounded-sm bg-u-raised px-1.5 py-px font-mono text-eyebrow font-medium text-u-text3"
        >
          <Icon d={ICONS.lock} size={10} />
          System
        </span>
      )}
      {isAdmin && (
        <span className="ms-auto flex items-center gap-1">
          {!isDefault && (
            <Button
              variant="ghost"
              size="xs"
              disabled={update.isPending}
              onClick={() => change({ isDefault: true }, "Default status changed")}
            >
              Make default
            </Button>
          )}
          {/* Keyed apart: patched in place, the pressed Rename became this form's Save and submitted it mid-click. */}
          {renaming ? (
            <Button
              key="save"
              type="submit"
              form={`rename-status-${status.id}`}
              variant="secondary"
              size="xs"
              disabled={!draft.trim()}
              loading={update.isPending}
            >
              Save
            </Button>
          ) : (
            <Button
              key="rename"
              type="button"
              variant="ghost"
              size="xs"
              onClick={() => {
                setDraft(status.label);
                setRenaming(true);
              }}
            >
              Rename
            </Button>
          )}
          {status.system ? (
            <span title="Built-in status — can be renamed, not retired" className="grid w-7 place-items-center text-u-text3">
              <Icon d={ICONS.lock} size={12} />
            </span>
          ) : (
            <Button
              variant="ghost"
              size="xs"
              title="Stop offering this status; candidates keep it"
              aria-label={`Retire ${status.label}`}
              disabled={update.isPending}
              onClick={() => change({ retired: true }, "Status retired; candidates who hold it keep it")}
            >
              <Icon d={ICONS.archive} size={13} />
            </Button>
          )}
        </span>
      )}
    </li>
  );
}
