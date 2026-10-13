import { DndContext, MeasuringStrategy, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { useState, type FormEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, Input, Modal, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { moveItem } from "../../../lib/moveItem";
import { useSortableSensors, VERTICAL_LIST_MODIFIERS } from "../../../lib/sortableSensors";
import { useAuth } from "../../auth/AuthProvider";
import { liveStages } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { PipelineStage } from "../../workspace/api/types";
import { usePipelineCatalog, usePipelineWrite } from "../../workspace/lib/usePipelineCatalog";
import { PipelineStageCard } from "../components/PipelineStageCard";
import { inOrderOf, PIPELINE_NAME_LIMIT } from "../lib/pipelineStageRows";
import { usePipelineReorder } from "../lib/usePipelineReorder";

/** A stage folds as it is picked up, so its neighbours are measured again while it moves. */
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
  const [newStage, setNewStage] = useState("");
  const [blockedStage, setBlockedStage] = useState<PipelineStage | null>(null);
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(new Set());
  const sensors = useSortableSensors();

  const addStage = usePipelineWrite((label: string) => pipelineApi.createStage(label));
  const reorder = usePipelineReorder(
    (ids) => pipelineApi.reorderStages(ids),
    (catalog, ids) => ({ stages: inOrderOf(catalog.stages, ids) }),
  );

  const stages = query.data ? liveStages(query.data) : [];
  const activeStages = stages.filter((stage) => stage.kind === "active");
  const closedStages = stages.filter((stage) => stage.kind === "closed");
  const activeIds = activeStages.map((stage) => stage.id);
  const allOpen = stages.length > 0 && stages.every((stage) => openIds.has(stage.id));

  const moveStage = (from: number, to: number) => {
    if (from < 0 || to < 0 || to >= activeIds.length || from === to) return;
    reorder([...moveItem(activeIds, from, to), ...closedStages.map((stage) => stage.id)]);
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
        const added = liveStages(catalog).find((stage) => stage.label.toLowerCase() === label.toLowerCase());
        if (added) setOpenIds((current) => new Set([...current, added.id]));
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
            modifiers={VERTICAL_LIST_MODIFIERS}
            measuring={STAGE_MEASURING}
            onDragEnd={handleStageDragEnd}
          >
            {/* The modifier bounds a drag to this element, so it holds the active stages alone and none passes Declined. */}
            <div className="flex flex-col gap-2">
              <SortableContext items={activeIds} strategy={verticalListSortingStrategy}>
                {activeStages.map((stage, index) => (
                  <PipelineStageCard
                    key={stage.id}
                    stage={stage}
                    isAdmin={isAdmin}
                    open={openIds.has(stage.id)}
                    onToggle={() => handleToggle(stage.id)}
                    onStep={isAdmin ? (step) => moveStage(index, index + step) : undefined}
                    onRetireBlocked={() => setBlockedStage(stage)}
                  />
                ))}
              </SortableContext>
            </div>
            <div className="mt-2 flex flex-col gap-2">
              {closedStages.map((stage) => (
                <PipelineStageCard
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
              maxLength={PIPELINE_NAME_LIMIT}
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
