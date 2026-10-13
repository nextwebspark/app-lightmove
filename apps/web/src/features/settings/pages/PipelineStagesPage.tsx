import {
  DndContext,
  KeyboardSensor,
  MeasuringStrategy,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { restrictToParentElement, restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, Input, Modal, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { moveItem } from "../../../lib/moveItem";
import { useAuth } from "../../auth/AuthProvider";
import {
  defaultStatusOf,
  liveStages,
  liveStatuses,
  PIPELINE_COLOURS,
  pipelineSwatchClass,
} from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type {
  PipelineCatalog,
  PipelineStage,
  PipelineStageChange,
  PipelineStatus,
  PipelineStatusChange,
} from "../../workspace/api/types";
import { usePipelineCatalog, usePipelineWrite } from "../../workspace/lib/usePipelineCatalog";

const NAME_LIMIT = 40;
const PREVIEWED_STATUSES = 3;
/** A few pixels before a drag starts, so a click on the handle is still a click. */
const POINTER_SENSOR = { activationConstraint: { distance: 4 } };
const KEYBOARD_SENSOR = { coordinateGetter: sortableKeyboardCoordinates };
const DRAG_MODIFIERS = [restrictToVerticalAxis, restrictToParentElement];
/** A stage folds while it is dragged, so its neighbours are measured again as it moves. */
const STAGE_MEASURING = { droppable: { strategy: MeasuringStrategy.Always } };

/**
 * Settings → Pipeline stages (`Settings.dc.html`, `pipeline-stages`): the workspace's stages in order, each
 * a row that opens onto its statuses. Every staff member reads it; only an admin changes it, and the server
 * re-checks.
 */
export function PipelineStagesPage() {
  const { user } = useAuth();
  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const { query } = usePipelineCatalog();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [newStage, setNewStage] = useState("");
  const [blockedStage, setBlockedStage] = useState<PipelineStage | null>(null);
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(new Set());
  // Hoisted options: a fresh object each render re-initialises DndContext and the reorder stops working.
  const sensors = useSensors(useSensor(PointerSensor, POINTER_SENSOR), useSensor(KeyboardSensor, KEYBOARD_SENSOR));

  const addStage = usePipelineWrite((label: string) => pipelineApi.createStage(label));
  const reorderStages = usePipelineWrite((ids: string[]) => pipelineApi.reorderStages(ids));

  const stages = query.data ? liveStages(query.data) : [];
  const activeStages = stages.filter((stage) => stage.kind === "active");
  const closedStages = stages.filter((stage) => stage.kind === "closed");
  const activeIds = activeStages.map((stage) => stage.id);
  const allOpen = stages.length > 0 && stages.every((stage) => openIds.has(stage.id));

  const moveStage = (from: number, to: number) => {
    if (from < 0 || to < 0 || to >= activeIds.length || from === to) return;
    const ids = [...moveItem(activeIds, from, to), ...closedStages.map((stage) => stage.id)];
    const previous = queryClient.getQueryData<PipelineCatalog>(pipelineApi.PIPELINE_CATALOG_KEY);
    if (previous) {
      queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, {
        stages: [...previous.stages].sort((a, b) => rankIn(ids, a.id) - rankIn(ids, b.id)),
      });
    }
    reorderStages.mutate(ids, {
      onError: (error) => {
        if (previous) queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, previous);
        toast.error(messageFor(error));
      },
    });
  };

  const handleStageDragEnd = ({ active, over }: DragEndEvent) => {
    if (over) moveStage(activeIds.indexOf(String(active.id)), activeIds.indexOf(String(over.id)));
  };

  const handleToggle = (id: string) =>
    setOpenIds((current) => {
      const next = new Set(current);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  const handleAddStage = (event: FormEvent) => {
    event.preventDefault();
    const label = newStage.trim();
    if (!label) return;
    addStage.mutate(label, {
      onSuccess: (catalog) => {
        const known = new Set(stages.map((stage) => stage.id));
        const added = liveStages(catalog).filter((stage) => !known.has(stage.id));
        setOpenIds((current) => new Set([...current, ...added.map((stage) => stage.id)]));
        setNewStage("");
        toast("Stage added to every position");
      },
      onError: (error) => toast.error(messageFor(error)),
    });
  };

  return (
    <>
      <PageHeader
        title="Pipeline stages"
        subtitle="Stages are the columns of a position's pipeline; every status belongs to one stage. A candidate holds a status, and moves between stages by changing it. A retired status stays on the candidates who hold it but is never offered again."
      />
      {!isAdmin && (
        <p className="-mt-2 mb-4 inline-flex items-center gap-2 rounded-u-control border border-u-border bg-u-raised px-2.5 py-1.5 font-mono text-meta text-u-text2">
          <Icon d={ICONS.lock} size={12} />
          Only workspace admins can change stages and statuses.
        </p>
      )}

      {query.isError ? (
        <p className="text-note text-u-text3">{messageFor(query.error)}</p>
      ) : query.isPending ? (
        <CardsSkeleton />
      ) : (
        <>
          <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
            <p className="font-mono text-meta text-u-text3">Open a stage to see its statuses.</p>
            <Button
              variant="ghost"
              size="xs"
              onClick={() => setOpenIds(allOpen ? new Set() : new Set(stages.map((stage) => stage.id)))}
            >
              {allOpen ? "Collapse all" : "Expand all"}
            </Button>
          </div>
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            modifiers={DRAG_MODIFIERS}
            measuring={STAGE_MEASURING}
            onDragEnd={handleStageDragEnd}
          >
            {/* The modifier bounds a drag to this element, so it holds the active stages alone and none passes Declined. */}
            <div className="flex flex-col gap-2">
              <SortableContext items={activeIds} strategy={verticalListSortingStrategy}>
                {activeStages.map((stage, index) => (
                  <StageCard
                    key={stage.id}
                    stage={stage}
                    isAdmin={isAdmin}
                    open={openIds.has(stage.id)}
                    onToggle={() => handleToggle(stage.id)}
                    onStep={(step) => moveStage(index, index + step)}
                    onRetireBlocked={() => setBlockedStage(stage)}
                  />
                ))}
              </SortableContext>
            </div>
            <div className="mt-2 flex flex-col gap-2">
              {closedStages.map((stage) => (
                <StageCard
                  key={stage.id}
                  stage={stage}
                  isAdmin={isAdmin}
                  open={openIds.has(stage.id)}
                  onToggle={() => handleToggle(stage.id)}
                  onRetireBlocked={() => setBlockedStage(stage)}
                />
              ))}
            </div>
          </DndContext>
        </>
      )}

      {isAdmin && query.isSuccess && (
        <>
          <form className="mt-3.5 flex flex-wrap items-center gap-2" onSubmit={handleAddStage}>
            <Input
              value={newStage}
              onChange={(event) => setNewStage(event.target.value)}
              placeholder="New stage, e.g. Assessed"
              aria-label="New stage"
              maxLength={NAME_LIMIT}
              className="max-w-[260px]"
            />
            <Button type="submit" size="sm" disabled={!newStage.trim()} loading={addStage.isPending}>
              <Icon d={ICONS.plus} size={14} />
              Add stage
            </Button>
          </form>
          <p className="mt-3 max-w-[620px] font-mono text-meta text-u-text3">
            A new stage is active and goes in before Declined, which stays last. A rename or a new colour reaches
            every position at once. Dropping a candidate on a stage&apos;s column gives them its default status.
          </p>
        </>
      )}

      <Modal
        open={blockedStage !== null}
        onClose={() => setBlockedStage(null)}
        title={`Retire ${blockedStage?.label ?? "stage"}`}
        footer={<Button onClick={() => setBlockedStage(null)}>OK</Button>}
      >
        <p className="text-body text-u-text2">
          Move or retire this stage&apos;s statuses first — a stage with live statuses can&apos;t be retired.
        </p>
      </Modal>
    </>
  );
}

function StageCard({
  stage,
  isAdmin,
  open,
  onToggle,
  onStep,
  onRetireBlocked,
}: {
  stage: PipelineStage;
  isAdmin: boolean;
  open: boolean;
  onToggle: () => void;
  onStep?: (step: number) => void;
  onRetireBlocked: () => void;
}) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [renaming, setRenaming] = useState(false);
  const [draft, setDraft] = useState(stage.label);
  const [newStatus, setNewStatus] = useState("");
  const updateStage = usePipelineWrite((change: PipelineStageChange) => pipelineApi.updateStage(stage.id, change));
  const addStatus = usePipelineWrite((label: string) => pipelineApi.createStatus(stage.id, label));
  const reorderStatuses = usePipelineWrite((ids: string[]) => pipelineApi.reorderStatuses(stage.id, ids));
  const statuses = liveStatuses(stage);
  const defaultStatus = defaultStatusOf(stage);
  const isClosed = stage.kind === "closed";
  const statusIds = statuses.map((status) => status.id);
  const sortable = useSortable({ id: stage.id, disabled: !isAdmin || isClosed });
  const sensors = useSensors(useSensor(PointerSensor, POINTER_SENSOR), useSensor(KeyboardSensor, KEYBOARD_SENSOR));
  const unfolded = open && !sortable.isDragging;
  const bodyId = `stage-${stage.id}-statuses`;
  const countLabel = statuses.length === 1 ? "1 status" : `${statuses.length} statuses`;
  const preview =
    statuses.length === 0
      ? "No statuses yet"
      : statuses
          .slice(0, PREVIEWED_STATUSES)
          .map((status) => status.label)
          .join(", ") + (statuses.length > PREVIEWED_STATUSES ? ` +${statuses.length - PREVIEWED_STATUSES}` : "");

  const moveStatus = (from: number, to: number) => {
    if (from < 0 || to < 0 || to >= statusIds.length || from === to) return;
    const ids = moveItem(statusIds, from, to);
    const previous = queryClient.getQueryData<PipelineCatalog>(pipelineApi.PIPELINE_CATALOG_KEY);
    if (previous) {
      queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, {
        stages: previous.stages.map((candidate) =>
          candidate.id === stage.id
            ? {
                ...candidate,
                statuses: [...candidate.statuses].sort((a, b) => rankIn(ids, a.id) - rankIn(ids, b.id)),
              }
            : candidate,
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

  const handleStatusDragEnd = ({ active, over }: DragEndEvent) => {
    if (over) moveStatus(statusIds.indexOf(String(active.id)), statusIds.indexOf(String(over.id)));
  };

  const change = (patch: PipelineStageChange, success?: string) =>
    updateStage.mutate(patch, {
      onSuccess: () => {
        if (patch.label !== undefined) setRenaming(false);
        if (success) toast(success);
      },
      onError: (error) => toast.error(messageFor(error)),
    });

  const handleRetire = () => {
    if (statuses.length > 0) {
      onRetireBlocked();
      return;
    }
    change({ retired: true }, `${stage.label} retired`);
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
    <section
      ref={sortable.setNodeRef}
      style={{ transform: CSS.Translate.toString(sortable.transform), transition: sortable.transition }}
      aria-label={stage.label}
      className={cn(
        "overflow-hidden rounded-u-card border border-u-border bg-u-surface",
        sortable.isDragging && "relative z-10 shadow-u-e3",
      )}
    >
      <div className={cn("flex flex-wrap items-center gap-2.5 px-4 py-2", unfolded && "border-b border-u-border")}>
        {isAdmin && !isClosed && onStep && (
          <DragHandle label={`Reorder ${stage.label} stage`} sortable={sortable} onStep={onStep} />
        )}
        {isAdmin && isClosed && (
          <span title="The closed stage stays last" className="grid w-3.5 place-items-center text-u-text3">
            <Icon d={ICONS.lock} size={12} />
          </span>
        )}
        <ColourDot
          colour={stage.colour}
          name={`${stage.label} stage`}
          size="stage"
          editable={isAdmin}
          onChange={(colour) => change({ colour })}
        />
        <button
          type="button"
          aria-expanded={unfolded}
          aria-controls={bodyId}
          aria-label={`${stage.label} stage, ${countLabel}`}
          title={unfolded ? "Hide statuses" : "Show statuses"}
          onClick={onToggle}
          className={cn(
            "flex min-w-0 items-center gap-2.5 rounded-u-control px-1.5 py-1.5 text-left transition hover:bg-u-raised",
            !renaming && "flex-1",
          )}
        >
          <Icon
            d={ICONS.chevronDown}
            size={15}
            className={cn(
              "flex-none text-u-text2 transition-transform duration-200 motion-reduce:transition-none",
              !unfolded && "-rotate-90",
            )}
          />
          {!renaming && (
            <>
              <span className="flex-none text-body font-semibold text-u-text">{stage.label}</span>
              <span
                className={cn(
                  "flex-none rounded-full px-2 py-0.5 font-mono text-meta font-medium",
                  isClosed ? "bg-u-raised text-u-text3" : "bg-u-accent-tint text-u-accent",
                )}
              >
                {isClosed ? "Closed" : "Active"}
              </span>
              <span className="flex-none font-mono text-meta text-u-text3">{countLabel}</span>
              {!unfolded && <span className="min-w-0 truncate font-mono text-meta text-u-text3">{preview}</span>}
            </>
          )}
        </button>
        {renaming && (
          <RenameForm
            id={`rename-stage-${stage.id}`}
            label="Stage name"
            draft={draft}
            onDraft={setDraft}
            onCancel={() => setRenaming(false)}
            onSave={() => change({ label: draft.trim() }, "Renamed on every position")}
          />
        )}
        {isAdmin && (
          <span className="ms-auto flex flex-none gap-1">
            {/* Keyed apart: patched in place, the pressed Rename became this form's Save and submitted it mid-click. */}
            {renaming ? (
              <Button
                key="save"
                type="submit"
                form={`rename-stage-${stage.id}`}
                variant="secondary"
                size="xs"
                disabled={!draft.trim()}
                loading={updateStage.isPending}
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
                  setDraft(stage.label);
                  setRenaming(true);
                }}
              >
                Rename
              </Button>
            )}
            {!isClosed && (
              <Button
                variant="ghost"
                size="xs"
                title="Retire this stage"
                aria-label={`Retire ${stage.label} stage`}
                disabled={updateStage.isPending}
                onClick={handleRetire}
              >
                <Icon d={ICONS.archive} size={14} />
              </Button>
            )}
          </span>
        )}
      </div>

      <div
        id={bodyId}
        aria-hidden={!unfolded}
        inert={!unfolded}
        className={cn(
          "grid",
          !sortable.isDragging && "transition-[grid-template-rows] duration-200 ease-out motion-reduce:transition-none",
        )}
        style={{ gridTemplateRows: unfolded ? "1fr" : "0fr" }}
      >
        <div className="min-h-0 overflow-hidden">
          {statuses.length === 0 && (
            <p className="px-4 py-3 ps-10 font-mono text-meta text-u-text3">
              No statuses yet. The first one you add becomes the default.
            </p>
          )}
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            modifiers={DRAG_MODIFIERS}
            onDragEnd={handleStatusDragEnd}
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
                maxLength={NAME_LIMIT}
                className="max-w-[240px] py-1.5"
              />
              <Button type="submit" variant="secondary" size="xs" disabled={!newStatus.trim()} loading={addStatus.isPending}>
                <Icon d={ICONS.plus} size={12} />
                Add status
              </Button>
            </form>
          )}
        </div>
      </div>
    </section>
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

function RenameForm({
  id,
  label,
  draft,
  onDraft,
  onCancel,
  onSave,
}: {
  id: string;
  label: string;
  draft: string;
  onDraft: (value: string) => void;
  onCancel: () => void;
  onSave: () => void;
}) {
  return (
    <form
      id={id}
      className="contents"
      onSubmit={(event) => {
        event.preventDefault();
        if (draft.trim()) onSave();
      }}
    >
      <Input
        value={draft}
        onChange={(event) => onDraft(event.target.value)}
        onKeyDown={(event) => event.key === "Escape" && onCancel()}
        aria-label={label}
        maxLength={NAME_LIMIT}
        autoFocus
        className="max-w-[220px] py-1"
      />
    </form>
  );
}

/** A click moves the swatch on to the next colour, the mockup's way of recolouring in place. */
function ColourDot({
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
  return (
    <button
      type="button"
      disabled={!editable}
      title={editable ? "Change colour" : undefined}
      aria-label={`Colour of ${name}`}
      onClick={() => onChange(next)}
      className={cn(
        "flex-none rounded-full disabled:cursor-default",
        size === "stage" ? "size-[11px]" : "size-[9px]",
        pipelineSwatchClass(colour),
      )}
    />
  );
}

/** The only part of a row that drags it; an arrow key on it moves the row one place without picking it up. */
function DragHandle({
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
    onStep(step);
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

function rankIn(ids: readonly string[], id: string): number {
  const index = ids.indexOf(id);
  return index === -1 ? Number.MAX_SAFE_INTEGER : index;
}
