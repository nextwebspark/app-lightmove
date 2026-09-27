import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button, ChoiceCardGroup, Modal, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import type { WorkspaceMode } from "../../auth/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import { WORKSPACE_MODE_OPTIONS } from "../../workspace/lib/workspaceModes";

/** Settings → General's workspace type. A switch is confirmed first: every colleague's screens change with it. */
export function WorkspaceModeCard({ mode }: { mode: WorkspaceMode }) {
  const { reload } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [proposed, setProposed] = useState<WorkspaceMode | null>(null);

  const change = useMutation({
    mutationFn: (next: WorkspaceMode) => workspaceApi.changeMode(next),
    onSuccess: async (saved) => {
      queryClient.setQueryData(workspaceApi.WORKSPACE_KEY, saved);
      setProposed(null);
      // The labels every screen draws read the mode from the auth summary.
      await reload();
      toast("Workspace type changed");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const handleChoose = (next: WorkspaceMode) => {
    if (next !== mode) setProposed(next);
  };

  const proposedTitle = WORKSPACE_MODE_OPTIONS.find((option) => option.value === proposed)?.title;

  return (
    <div className="mt-4 rounded-[10px] border border-u-border bg-u-raised p-5">
      <div className="mb-4">
        <div className="text-sm font-semibold">Workspace type</div>
        <div className="mt-0.5 font-mono text-[11.5px] text-u-text3">
          Who you hire for — it names your clients and decides whose profile the assistant works from.
        </div>
      </div>

      <ChoiceCardGroup label="Workspace type" options={WORKSPACE_MODE_OPTIONS} value={mode} onChange={handleChoose} />

      {proposed && (
        <Modal
          open
          onClose={() => setProposed(null)}
          title={`Switch to ${proposedTitle}?`}
          footer={
            <>
              <Button variant="secondary" onClick={() => setProposed(null)}>
                Cancel
              </Button>
              <Button loading={change.isPending} onClick={() => change.mutate(proposed)}>
                Switch
              </Button>
            </>
          }
        >
          <p className="text-[13px] text-u-text2">
            Everyone in this workspace will see the new labels and screens. Clients, positions and everything
            mapped for them are kept exactly as they are.
          </p>
        </Modal>
      )}
    </div>
  );
}
