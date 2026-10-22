import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Toggle, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as notetakerApi from "../../outreach/api/notetakerApi";
import { platformListOf } from "../../outreach/lib/notetakerChip";

/**
 * Settings → Profile's "Uncava Notetaker on my calls" (`Settings.dc.html`). Saved as it is switched — its own audited
 * write, never part of Save changes — and greyed while the workspace has the Notetaker off.
 */
export function MyNotetakerRow() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const mine = useQuery({
    queryKey: notetakerApi.MY_NOTETAKER_KEY,
    queryFn: ({ signal }) => notetakerApi.getMyNotetaker(signal),
  });
  const workspace = useQuery({
    queryKey: notetakerApi.WORKSPACE_NOTETAKER_KEY,
    queryFn: ({ signal }) => notetakerApi.getWorkspaceNotetaker(signal),
    enabled: mine.data?.offered === true,
  });
  const change = useMutation({
    mutationFn: (enabled: boolean) => notetakerApi.setMyNotetaker(enabled),
    onSuccess: (saved) => {
      queryClient.setQueryData(notetakerApi.MY_NOTETAKER_KEY, saved);
      void queryClient.invalidateQueries({ queryKey: ["outreach"] });
      toast(saved.enabled ? "The notetaker will join your calls" : "The notetaker won't join your calls");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  if (!mine.isSuccess || !mine.data.offered) return null;
  const { workspaceEnabled, enabled } = mine.data;
  const platforms = platformListOf(workspace.data?.platforms ?? []) ?? "Google Meet";

  return (
    <div className="mt-1 flex items-start gap-3 rounded-lg border border-u-border bg-u-surface px-3.5 py-3">
      <span className="min-w-0 flex-1">
        <span className="block text-body font-semibold">Uncava Notetaker on my calls</span>
        <span className="mt-0.5 block font-mono text-meta text-u-text3">
          Joins your {platforms} calls with executives and files a meeting note afterwards. Turn it off for one call
          from the meeting in an executive's drawer.
        </span>
        {!workspaceEnabled && (
          <span className="mt-1.5 block text-note text-u-text2">
            Your workspace has the notetaker turned off. An admin turns it on in Integrations.
          </span>
        )}
      </span>
      <Toggle
        checked={workspaceEnabled && enabled}
        label="Uncava Notetaker on my calls"
        disabled={!workspaceEnabled || change.isPending}
        onChange={(next) => change.mutate(next)}
      />
    </div>
  );
}
