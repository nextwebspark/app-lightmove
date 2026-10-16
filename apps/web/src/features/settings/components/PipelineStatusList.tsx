import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useState, type FormEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { moveItem } from "../../../lib/moveItem";
import { useSortableSensors, VERTICAL_LIST_MODIFIERS } from "../../../lib/sortableSensors";
import { defaultStatusOf, liveStatuses } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type {
  PipelineRetirePreview,
  PipelineStage,
  PipelineStatus,
  PipelineStatusChange,
  StatusAutomation,
} from "../../workspace/api/types";
import { automationsInto, triggerCopyOf, useStatusAutomations } from "../../workspace/lib/statusAutomation";
import { usePipelineCatalog, usePipelineWrite } from "../../workspace/lib/usePipelineCatalog";
import { inOrderOf, PIPELINE_NAME_LIMIT, retireTouchesAnyone } from "../lib/pipelineStageRows";
import { usePipelineReorder } from "../lib/usePipelineReorder";
import { usePipelineRowChange, useRenameDraft } from "../lib/usePipelineRowEdits";
import { PipelineRetireDialog } from "./PipelineRetireDialog";
import { ColourDot, DragHandle, RenameActions, RenameForm } from "./PipelineRowControls";

/** One stage's statuses, reordered within the stage alone, and the form that adds one. */
export function PipelineStatusList({ stage, isAdmin }: { stage: PipelineStage; isAdmin: boolean }) {
  const toast = useToast();
  const sensors = useSortableSensors();
  const [newStatus, setNewStatus] = useState("");
  const addStatus = usePipelineWrite((label: string) => pipelineApi.createStatus(stage.id, label));
  const automations = useStatusAutomations().data ?? [];
  const statuses = liveStatuses(stage);
  const defaultStatus = defaultStatusOf(stage);
  const statusIds = statuses.map((status) => status.id);

  const reorder = usePipelineReorder(
    (ids) => pipelineApi.reorderStatuses(stage.id, ids),
    (catalog, ids) => ({
      stages: catalog.stages.map((candidate) =>
        candidate.id === stage.id ? { ...candidate, statuses: inOrderOf(candidate.statuses, ids) } : candidate,
      ),
    }),
  );

  const moveStatus = (from: number, to: number) => {
    if (from < 0 || to < 0 || to >= statusIds.length || from === to) return;
    reorder(moveItem(statusIds, from, to));
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
                automations={automationsInto(automations, status.id)}
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
  automations,
  onStep,
}: {
  status: PipelineStatus;
  isDefault: boolean;
  isAdmin: boolean;
  /** The switched-on automations that move people to this status. */
  automations: StatusAutomation[];
  onStep: (step: number) => void;
}) {
  const toast = useToast();
  const { catalog } = usePipelineCatalog();
  const [preview, setPreview] = useState<PipelineRetirePreview | null>(null);
  const [checking, setChecking] = useState(false);
  const sortable = useSortable({ id: status.id, disabled: !isAdmin });
  const rename = useRenameDraft(status.label);
  const { change, isPending } = usePipelineRowChange(
    (patch: PipelineStatusChange) => pipelineApi.updateStatus(status.id, patch),
    rename,
  );
  const renameFormId = `rename-status-${status.id}`;
  const retire = () => change({ retired: true }, "Status retired; candidates who hold it keep it");

  const handleRetire = async () => {
    setChecking(true);
    try {
      const read = await pipelineApi.statusRetirePreview(status.id);
      if (retireTouchesAnyone(read)) setPreview(read);
      else retire();
    } catch (error) {
      toast.error(messageFor(error));
    } finally {
      setChecking(false);
    }
  };

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
      {rename.renaming ? (
        <RenameForm
          formId={renameFormId}
          label="Status name"
          rename={rename}
          onSave={(label) => change({ label }, "Renamed on every position")}
        />
      ) : (
        <span className="text-note text-u-text">{status.label}</span>
      )}
      {isDefault && (
        <span className="rounded-sm bg-u-accent-tint px-1.5 py-px font-mono text-eyebrow font-medium text-u-accent">
          Default
        </span>
      )}
      {automations.map((automation) => (
        <span
          key={automation.trigger}
          title={`${triggerCopyOf(automation.trigger).when}, candidates move here automatically`}
          className="inline-flex items-center gap-1 rounded-sm bg-u-raised px-1.5 py-px font-mono text-eyebrow font-medium text-u-text2"
        >
          <Icon d={ICONS.activity} size={10} />
          Auto · {triggerCopyOf(automation.trigger).short}
        </span>
      ))}
      {isAdmin && (
        <span className="ms-auto flex items-center gap-1">
          {!isDefault && (
            <Button
              variant="ghost"
              size="xs"
              disabled={isPending}
              onClick={() => change({ isDefault: true }, "Default status changed")}
            >
              Make default
            </Button>
          )}
          <RenameActions formId={renameFormId} rename={rename} saving={isPending} />
          <Button
            variant="ghost"
            size="xs"
            title="Stop offering this status; candidates keep it"
            aria-label={`Retire ${status.label}`}
            disabled={isPending || checking}
            onClick={() => void handleRetire()}
          >
            <Icon d={ICONS.archive} size={13} />
          </Button>
        </span>
      )}
      <PipelineRetireDialog
        open={preview !== null}
        title={`Retire ${status.label}?`}
        confirmLabel="Retire status"
        catalog={catalog}
        preview={preview}
        pending={isPending}
        onConfirm={() => {
          setPreview(null);
          retire();
        }}
        onClose={() => setPreview(null)}
      />
    </li>
  );
}
