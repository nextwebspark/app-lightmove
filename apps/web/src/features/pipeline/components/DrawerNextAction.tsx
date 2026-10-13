import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import * as pipelineApi from "../api/pipelineApi";
import { useNextAction } from "../lib/usePipelineWrites";
import { NextActionEditor, NextActionText } from "./PipelineControls";

/**
 * The executive drawer header's next action, read off the position's pipeline and set from here as
 * from the Pipeline page. Staff-only, like the pipeline itself.
 */
export function DrawerNextAction({ projectId, candidateId }: { projectId: string; candidateId: string }) {
  const [editing, setEditing] = useState(false);
  const save = useNextAction(projectId);
  const pipeline = useQuery({
    queryKey: pipelineApi.PIPELINE_KEY(projectId, pipelineApi.NO_PIPELINE_QUERY),
    queryFn: ({ signal }) => pipelineApi.getPipeline(projectId, pipelineApi.NO_PIPELINE_QUERY, signal),
  });
  const row = pipeline.data?.rows.find((candidate) => candidate.candidateId === candidateId);
  if (!row) return null;

  return (
    <div className="mt-2 flex items-start gap-2 text-meta">
      <span className="mt-px flex-none font-mono text-u-text3">Next action</span>
      {editing ? (
        <div className="min-w-0 flex-1">
          <NextActionEditor
            nextAction={row.nextAction}
            saving={save.isPending}
            onCancel={() => setEditing(false)}
            onSave={(text, dueAt) =>
              save.mutate({ candidateId, text, dueAt }, { onSuccess: () => setEditing(false) })
            }
          />
        </div>
      ) : (
        <button
          type="button"
          aria-label="Edit next action"
          onClick={() => setEditing(true)}
          className="flex min-w-0 items-start gap-1.5 rounded-u-control text-start hover:bg-u-sunken"
        >
          <NextActionText nextAction={row.nextAction} />
          <Icon d={ICONS.pencil} size={11} className="mt-0.5 flex-none text-u-text3" />
        </button>
      )}
    </div>
  );
}
