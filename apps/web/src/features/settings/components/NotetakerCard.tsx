import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AppIcon, Toggle, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as notetakerApi from "../../outreach/api/notetakerApi";
import { platformListOf } from "../../outreach/lib/notetakerChip";

/**
 * Settings → Integrations → Uncava Notetaker (`Settings.dc.html`): the workspace's switch for the bot on calls with
 * executives. Drawn only where the deployment sends one; the page is an admin's, as is the switch.
 */
export function NotetakerCard() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const notetaker = useQuery({
    queryKey: notetakerApi.WORKSPACE_NOTETAKER_KEY,
    queryFn: ({ signal }) => notetakerApi.getWorkspaceNotetaker(signal),
  });
  const change = useMutation({
    mutationFn: (enabled: boolean) => notetakerApi.setWorkspaceNotetaker(enabled),
    onSuccess: (saved) => {
      queryClient.setQueryData(notetakerApi.WORKSPACE_NOTETAKER_KEY, saved);
      void queryClient.invalidateQueries({ queryKey: notetakerApi.MY_NOTETAKER_KEY });
      toast(saved.enabled ? "The notetaker is on for the workspace" : "The notetaker is off for the workspace");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const changeBackup = useMutation({
    mutationFn: (next: boolean) => notetakerApi.setWorkspaceNotetakerBackup(next),
    onSuccess: (saved) => {
      queryClient.setQueryData(notetakerApi.WORKSPACE_NOTETAKER_KEY, saved);
      toast(saved.backupEnabled ? "The backup recorder is on" : "The backup recorder is off");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  if (!notetaker.isSuccess || !notetaker.data.offered) return null;
  const { enabled, platforms, backupOffered, backupEnabled } = notetaker.data;
  const joins = platformListOf(platforms);

  return (
    <section aria-label="Uncava Notetaker" className="rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-[12rem] flex-1 items-start gap-3">
          <span className="flex size-9 flex-none items-center justify-center rounded-lg border border-u-border bg-u-surface">
            <AppIcon className="h-[22px]" />
          </span>
          <div className="min-w-0">
            <div className="text-sm font-semibold">Uncava Notetaker</div>
            <div className="mt-0.5 font-mono text-meta text-u-text3">
              {joins ? `Joins ${joins} calls with executives.` : "Joins no calls yet."}
            </div>
          </div>
        </div>
        <span className="inline-flex items-center gap-2 text-note font-medium text-u-text2">
          {enabled ? "On for the workspace" : "Off for the workspace"}
          <Toggle
            checked={enabled}
            label="Uncava Notetaker for the workspace"
            disabled={change.isPending}
            onChange={(next) => change.mutate(next)}
          />
        </span>
      </div>
      <p className="rounded-lg border border-u-border bg-u-surface px-3.5 py-3 text-note text-u-text2">
        The notetaker joins as <strong className="font-semibold text-u-text">Uncava Notetaker</strong>, says in the
        call's chat that it is recording, and leaves without recording if it cannot. After the call, a meeting note and
        its transcript are filed on the executive, visible to your team only.
      </p>
      <p className="mt-2.5 font-mono text-meta text-u-text3">
        Each person can turn it off for their own calls in Profile, and for one call from the meeting in an executive's
        drawer.
      </p>
      {backupOffered && (
        <div className="mt-4 flex flex-wrap items-start justify-between gap-3 border-t border-u-border pt-4">
          <div className="min-w-[12rem] flex-1">
            <div className="text-note font-semibold">Backup recorder</div>
            <p className="mt-1 text-note text-u-text2">
              When the notetaker can't join or drops out, a Recall.ai bot records the call instead, and joins Zoom
              calls. The call's audio and transcript then pass through Recall.ai, a third party, so it stays off until
              you turn it on.
            </p>
          </div>
          <span className="inline-flex items-center gap-2 text-note font-medium text-u-text2">
            {backupEnabled ? "On" : "Off"}
            <Toggle
              checked={backupEnabled}
              label="Backup recorder for the workspace"
              disabled={changeBackup.isPending}
              onChange={(next) => changeBackup.mutate(next)}
            />
          </span>
        </div>
      )}
    </section>
  );
}
