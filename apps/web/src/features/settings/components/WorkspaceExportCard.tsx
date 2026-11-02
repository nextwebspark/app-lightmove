import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, LinesSkeleton, Spinner, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { saveBlob } from "../../../lib/saveBlob";
import * as dataRetentionApi from "../api/dataRetentionApi";
import type { WorkspaceExport } from "../api/dataRetentionApi";
import { dayOf, expiresInPhrase, exportSizeLabel, momentOf } from "../lib/dataRetention";

/** While an export builds the card asks again on its own; the build is minutes, not seconds. */
const BUILDING_POLL_MS = 5_000;

/**
 * Settings → Data & retention's "Export workspace data" (`Settings.dc.html`, `data`): one zip of everything the
 * workspace holds, built in the background and emailed for when it is ready. Starting one and every download are
 * audited server-side.
 */
export function WorkspaceExportCard({ workspaceName }: { workspaceName: string }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [downloading, setDownloading] = useState(false);

  const latest = useQuery({
    queryKey: dataRetentionApi.WORKSPACE_EXPORT_KEY,
    queryFn: ({ signal }) => dataRetentionApi.getLatestWorkspaceExport(signal),
    refetchInterval: (query) => (query.state.data?.status === "RUNNING" ? BUILDING_POLL_MS : false),
  });
  const start = useMutation({
    mutationFn: dataRetentionApi.startWorkspaceExport,
    onSuccess: (started) => {
      queryClient.setQueryData(dataRetentionApi.WORKSPACE_EXPORT_KEY, started);
      void queryClient.invalidateQueries({ queryKey: dataRetentionApi.WORKSPACE_EXPORT_KEY });
      toast("Export started — we'll email you when it's ready");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const handleDownload = async (ready: WorkspaceExport) => {
    setDownloading(true);
    try {
      saveBlob(await dataRetentionApi.downloadWorkspaceExport(ready.id), ready.fileName);
    } catch (error) {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: dataRetentionApi.WORKSPACE_EXPORT_KEY });
    } finally {
      setDownloading(false);
    }
  };

  const current = latest.data ?? null;
  const isBuilding = current?.status === "RUNNING" || start.isPending;

  return (
    <section aria-label="Export workspace data" className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="text-body font-semibold text-u-text">Export workspace data</div>
          <p className="mt-0.5 max-w-[560px] font-mono text-meta text-u-text3">
            A zip of everything in {workspaceName}: positions, candidates, notes, documents and the audit log. It is
            built in the background, and we email you when it's ready.
          </p>
        </div>
        {current?.status !== "READY" && (
          <Button size="sm" loading={isBuilding} disabled={isBuilding || latest.isPending} onClick={() => start.mutate()}>
            {isBuilding ? "Exporting…" : "Start export"}
          </Button>
        )}
      </div>

      {latest.isError ? (
        <p className="mt-3.5 text-note text-u-text3">{messageFor(latest.error)}</p>
      ) : latest.isPending ? (
        <div className="mt-3.5">
          <LinesSkeleton />
        </div>
      ) : current?.status === "RUNNING" ? (
        <div
          role="status"
          className="mt-3.5 flex items-center gap-3 rounded-u-chip border border-u-border bg-u-surface px-3.5 py-3"
        >
          <span className="flex-none text-u-accent">
            <Spinner />
          </span>
          <div className="min-w-0 flex-1">
            <div className="text-body font-medium text-u-text">Building your export…</div>
            <div className="mt-0.5 font-mono text-meta text-u-text3">
              Started{current.requestedByName ? ` by ${current.requestedByName}` : ""} at {momentOf(current.requestedAt)}.
              You can leave this page.
            </div>
          </div>
        </div>
      ) : current?.status === "READY" ? (
        <div className="mt-3.5 flex flex-wrap items-center gap-3 rounded-u-chip border border-u-border bg-u-surface px-3.5 py-3">
          <span className="grid size-8 flex-none place-items-center rounded-u-chip bg-u-direct-tint text-u-direct">
            <Icon d={ICONS.file} size={16} />
          </span>
          <div className="min-w-0 flex-1">
            <div className="truncate font-mono text-body font-medium text-u-text">{current.fileName}</div>
            <div className="mt-0.5 font-mono text-meta text-u-text3">
              {readyLine(current)}
              {current.expiresAt && (
                <>
                  {" · "}
                  <span className="whitespace-nowrap text-u-signal">{expiresInPhrase(current.expiresAt)}</span>
                </>
              )}
            </div>
          </div>
          <Button variant="ghost" size="sm" disabled={start.isPending} onClick={() => start.mutate()}>
            Start a new export
          </Button>
          <Button size="sm" loading={downloading} onClick={() => void handleDownload(current)}>
            <Icon d={ICONS.download} size={14} />
            Download
          </Button>
        </div>
      ) : current?.status === "FAILED" ? (
        <p className="mt-3.5 font-mono text-meta text-u-offlimits">
          The export started {dayOf(current.requestedAt)} could not be built. Start it again.
        </p>
      ) : current?.status === "EXPIRED" && current.expiresAt ? (
        <p className="mt-3.5 font-mono text-meta text-u-text3">
          The last export expired on {dayOf(current.expiresAt)} and was deleted.
        </p>
      ) : null}

      <p className="mx-0.5 mt-3 font-mono text-meta text-u-text3">
        Starting an export and every download are recorded in the audit log. Only admins can start one.
      </p>
    </section>
  );
}

function readyLine(ready: WorkspaceExport): string {
  const parts: string[] = [];
  if (ready.sizeBytes !== null) parts.push(exportSizeLabel(ready.sizeBytes));
  if (ready.completedAt) {
    parts.push(`built ${momentOf(ready.completedAt)}${ready.requestedByName ? ` by ${ready.requestedByName}` : ""}`);
  }
  return parts.join(" · ");
}
