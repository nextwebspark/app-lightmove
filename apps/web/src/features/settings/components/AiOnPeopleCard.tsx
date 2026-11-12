import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Toggle, useToast } from "../../../components/ui";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { messageFor } from "../../../lib/errorCodes";
import type { AiControls } from "../../workspace/api/types";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import { AI_SWITCHES, clearingConfirmation, type ClearingInference } from "../lib/aiOnPeople";

/**
 * Settings → Data & retention's AI on people (#922): what the AI may do with the workspace's people. Turning on acts
 * at once; turning gender or nationality off clears what the AI suggested and nobody confirmed, so it asks first and
 * says how many people it touches.
 */
export function AiOnPeopleCard({ controls }: { controls: AiControls }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [clearing, setClearing] = useState<ClearingInference | null>(null);

  const held = useQuery({
    queryKey: workspaceApi.INFERRED_BACKGROUND_KEY,
    queryFn: workspaceApi.inferredBackground,
    enabled: clearing !== null,
  });

  const change = useMutation({
    mutationFn: (next: AiControls) => workspaceApi.changeAiControls(next),
    onSuccess: (saved) => {
      queryClient.setQueryData(workspaceApi.WORKSPACE_KEY, saved);
      void queryClient.invalidateQueries({ queryKey: workspaceApi.INFERRED_BACKGROUND_KEY });
      setClearing(null);
      toast("AI on people saved");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleToggle = (key: keyof AiControls, next: boolean) => {
    if (change.isPending) return;
    if (!next && (key === "inferGender" || key === "inferNationality")) {
      setClearing(key);
      return;
    }
    change.mutate({ ...controls, [key]: next });
  };

  const confirmation = clearing ? clearingConfirmation(clearing, held.data) : null;

  return (
    <section aria-label="AI on people" className="rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="text-body font-semibold text-u-text">AI on people</div>
      <p className="mt-0.5 font-mono text-meta text-u-text3">What the AI may work out about the people you hold.</p>
      <ul className="mt-3.5 flex flex-col divide-y divide-u-border">
        {AI_SWITCHES.map((item) => (
          <li key={item.key} className="flex items-start justify-between gap-4 py-3 first:pt-0 last:pb-0">
            <div className="min-w-0">
              <div className="text-note font-medium text-u-text">{item.label}</div>
              <p className="mt-0.5 text-note text-u-text3">{item.description}</p>
            </div>
            <Toggle
              label={item.label}
              checked={controls[item.key]}
              disabled={change.isPending}
              onChange={(next) => handleToggle(item.key, next)}
            />
          </li>
        ))}
      </ul>
      <div className="mt-3.5 flex gap-2.5 rounded-u-chip border border-u-border bg-u-surface px-3 py-2.5 font-mono text-meta text-u-text2">
        <Icon d={ICONS.info} size={15} className="mt-px flex-none text-u-text3" />
        <span>
          Turning an inference off clears what the AI suggested that nobody has confirmed. Values your team recorded
          stay. A consultant reviews every suggestion and decides.
        </span>
      </div>
      {clearing && confirmation && (
        <ConfirmDialog
          open
          title={confirmation.title}
          confirmLabel="Turn off and clear"
          pending={change.isPending}
          confirmDisabled={held.isPending}
          onConfirm={() => change.mutate({ ...controls, [clearing]: false })}
          onClose={() => setClearing(null)}
        >
          <p>{held.isError ? messageFor(held.error) : confirmation.body}</p>
          <p>This can't be undone.</p>
        </ConfirmDialog>
      )}
    </section>
  );
}
