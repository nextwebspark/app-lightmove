import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, useSortable, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useState, type FormEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Input, useToast } from "../../../components/ui";
import { ActionMenu, MenuItem, MenuSeparator } from "../../../components/ui/ActionMenu";
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
import { ColourDot, ColourSwatches, DragHandle, RenameActions, RenameForm } from "./PipelineRowControls";

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
                stageLabel={stage.label}
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
  stageLabel,
  isDefault,
  isAdmin,
  automations,
  onStep,
}: {
  status: PipelineStatus;
  stageLabel: string;
  isDefault: boolean;
  isAdmin: boolean;
  /** The switched-on automations that move people to this status. */
  automations: StatusAutomation[];
  onStep: (step: number) => void;
}) {
  const toast = useToast();
  const { catalog } = usePipelineCatalog();
  const [preview, setPreview] = useState<PipelineRetirePreview | null>(null);
  const [outPreview, setOutPreview] = useState<PipelineRetirePreview | null>(null);
  const [checking, setChecking] = useState(false);
  const sortable = useSortable({ id: status.id, disabled: !isAdmin });
  const rename = useRenameDraft(status.label);
  const { change, isPending } = usePipelineRowChange(
    (patch: PipelineStatusChange) => pipelineApi.updateStatus(status.id, patch),
    rename,
  );
  const renameFormId = `rename-status-${status.id}`;
  const retire = () => change({ retired: true }, "Status retired; candidates who hold it keep it");
  const markOut = () => change({ outOfProcess: true }, "Out of process; whoever holds it is now Inactive");

  const handleOutOfProcess = async (out: boolean) => {
    if (!out) {
      change({ outOfProcess: false }, "Back in process; whoever holds it returns to its stage");
      return;
    }
    setChecking(true);
    try {
      const read = await pipelineApi.statusRetirePreview(status.id);
      if (retireTouchesAnyone(read)) setOutPreview(read);
      else markOut();
    } catch (error) {
      toast.error(messageFor(error));
    } finally {
      setChecking(false);
    }
  };

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
      <ColourDot colour={status.colour} size="status" />
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
      {status.outOfProcess && (
        <span
          title="Whoever holds it is Inactive on the board and is not approached again"
          className="rounded-sm bg-u-offlimits-tint px-1.5 py-px font-mono text-eyebrow font-medium text-u-offlimits"
        >
          Out of process
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
          <RenameActions formId={renameFormId} rename={rename} saving={isPending} />
          {!rename.renaming && (
            <ActionMenu label={`${status.label} options`}>
              {(close) => {
                const choose = (action: () => void) => () => {
                  close();
                  action();
                };
                return (
                  <>
                    <ColourSwatches
                      colour={status.colour}
                      onChange={(colour) => {
                        close();
                        change({ colour });
                      }}
                    />
                    <MenuItem icon={ICONS.pencil} onSelect={choose(rename.begin)}>
                      Rename
                    </MenuItem>
                    {!isDefault && !status.outOfProcess && (
                      <MenuItem
                        icon={ICONS.star}
                        onSelect={choose(() => change({ isDefault: true }, "Default status changed"))}
                      >
                        Make default
                      </MenuItem>
                    )}
                    <MenuSeparator />
                    {status.outOfProcess ? (
                      <MenuItem
                        icon={ICONS.back}
                        note={`Whoever holds it returns to ${stageLabel}`}
                        disabled={isPending || checking}
                        onSelect={choose(() => void handleOutOfProcess(false))}
                      >
                        Bring back into process
                      </MenuItem>
                    ) : (
                      <MenuItem
                        icon={ICONS.ban}
                        note={isDefault ? "The default stays in process" : "Whoever holds it moves to Inactive"}
                        disabled={isDefault || isPending || checking}
                        onSelect={choose(() => void handleOutOfProcess(true))}
                      >
                        Mark out of process
                      </MenuItem>
                    )}
                    <MenuSeparator />
                    <MenuItem
                      icon={ICONS.archive}
                      tone="danger"
                      note="Stop offering it; candidates keep it"
                      disabled={isPending || checking}
                      onSelect={choose(() => void handleRetire())}
                    >
                      Retire
                    </MenuItem>
                  </>
                );
              }}
            </ActionMenu>
          )}
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
      <PipelineRetireDialog
        open={outPreview !== null}
        title={`Mark ${status.label} out of process?`}
        confirmLabel="Mark out of process"
        catalog={catalog}
        preview={outPreview}
        pending={isPending}
        explanation="They move to Inactive on every position, and any live sequence stops before its next email. Nothing is deleted, and you can bring the status back into the process at any time."
        onConfirm={() => {
          setOutPreview(null);
          markOut();
        }}
        onClose={() => setOutPreview(null)}
      />
    </li>
  );
}
