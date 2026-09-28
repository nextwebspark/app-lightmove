import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, ChoiceCardGroup, Modal, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import type { WorkspaceMode } from "../../auth/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import { WORKSPACE_MODE_OPTIONS } from "../../workspace/lib/workspaceModes";

/**
 * Settings → General's workspace type, admin-only like the rest of the page (the route is behind
 * `RequireAdmin`, the endpoint behind WORKSPACE_MANAGE). A switch is confirmed through a warning first:
 * every colleague's screens change with it.
 */
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
          Who you hire for — it decides what your clients are called and how they are shown. Only admins can
          change it.
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
                Switch to {proposedTitle}
              </Button>
            </>
          }
        >
          <div
            role="alert"
            className="mb-3 flex items-start gap-2.5 rounded-lg border border-u-signal bg-u-signal-tint px-3 py-2.5 text-[13px] text-u-text"
          >
            <span className="mt-0.5 flex-none text-u-signal">
              <Icon d={ICONS.warning} size={15} />
            </span>
            <span>This changes the workspace for everyone in it, not just for you.</span>
          </div>
          <ul className="list-disc space-y-1 ps-5 text-[13px] text-u-text2">
            <li>Every colleague and client contact sees the new labels and screens on their next page load.</li>
            <li>Clients, positions and everything mapped for them are kept exactly as they are.</li>
          </ul>
        </Modal>
      )}
    </div>
  );
}
