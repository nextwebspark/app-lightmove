import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { FormError, LinesSkeleton, Select } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { formatDate } from "../../../../lib/datetime/format";
import { messageFor } from "../../../../lib/errorCodes";
import * as promptApi from "../api/promptApi";
import type { PromptDetail, PromptVersionSummary } from "../api/types";
import { lineDiff, type DiffLineKind } from "../lib/lineDiff";
import { SETTINGS, settingLabel, versionLabel } from "../lib/promptLabels";

const LINE_CLASS: Record<DiffLineKind, string> = {
  same: "text-u-text2",
  added: "bg-u-direct-tint text-u-text",
  removed: "bg-u-offlimits-tint text-u-text",
  gap: "text-u-text3",
};

const LINE_SIGN: Record<DiffLineKind, string> = { same: " ", added: "+", removed: "−", gap: "⋯" };

/** Compare versions: the text, model and settings of any two versions of one prompt. */
export function VersionCompareTab({ detail, openVersion }: { detail: PromptDetail; openVersion: number }) {
  const live = detail.prompt.liveVersion;
  const [from, setFrom] = useState(openVersion === live ? (olderThan(detail, live) ?? live) : live);
  const [to, setTo] = useState(openVersion);

  const fromBody = useVersionBody(detail.prompt.id, from);
  const toBody = useVersionBody(detail.prompt.id, to);
  const lines = useMemo(
    () => (fromBody.data && toBody.data ? lineDiff(fromBody.data.body, toBody.data.body) : []),
    [fromBody.data, toBody.data],
  );

  const fromVersion = summaryOf(detail, from);
  const toVersion = summaryOf(detail, to);
  const rows = [
    {
      what: "Model",
      from: fromVersion?.modelName ?? "Default model",
      to: toVersion?.modelName ?? "Default model",
    },
    ...SETTINGS.map(({ key, label }) => ({
      what: label,
      from: settingLabel(fromVersion?.settings[key] ?? null, detail.defaults[key]),
      to: settingLabel(toVersion?.settings[key] ?? null, detail.defaults[key]),
    })),
  ];
  const failure = fromBody.error ?? toBody.error;

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-2.5">
        <VersionSelect label="From version" detail={detail} value={from} onChange={setFrom} />
        <span aria-hidden="true" className="text-u-text3">
          →
        </span>
        <VersionSelect label="To version" detail={detail} value={to} onChange={setTo} />
      </div>

      <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-sunken">
        {rows.map((row) => (
          <div
            key={row.what}
            className={cn(
              "flex flex-wrap items-center gap-2.5 border-b border-u-border px-3.5 py-2 text-body",
              row.from === row.to ? "text-u-text3" : "text-u-text",
            )}
          >
            <span className="w-[150px] flex-none type-micro-label font-mono text-u-text3">{row.what}</span>
            <span>{row.from}</span>
            <span aria-hidden="true" className="text-u-text3">
              →
            </span>
            <span>{row.to}</span>
          </div>
        ))}
        {failure ? (
          <div className="p-3.5">
            <FormError message={messageFor(failure)} />
          </div>
        ) : fromBody.isPending || toBody.isPending ? (
          <LinesSkeleton lines={6} className="p-3.5" />
        ) : lines.length === 0 ? (
          <p className="px-3.5 py-3 text-body text-u-text3">The text of these two versions is the same.</p>
        ) : (
          <div aria-label="Changes to the text" className="py-2">
            {lines.map((line, index) => (
              <div key={index} className={cn("flex gap-2 whitespace-pre-wrap px-3.5 font-u-num text-xs", LINE_CLASS[line.kind])}>
                <span aria-hidden="true" className="w-2.5 flex-none opacity-70">
                  {LINE_SIGN[line.kind]}
                </span>
                <span className="min-w-0">{line.text}</span>
              </div>
            ))}
          </div>
        )}
      </div>
      <span className="mt-1.5 block font-mono text-xs text-u-text3">Unchanged lines far from a change are left out.</span>
    </div>
  );
}

function VersionSelect({
  label,
  detail,
  value,
  onChange,
}: {
  label: string;
  detail: PromptDetail;
  value: number;
  onChange: (version: number) => void;
}) {
  return (
    <Select aria-label={label} value={value} onChange={(event) => onChange(Number(event.target.value))} className="w-auto">
      {detail.versions.map((version) => (
        <option key={version.version} value={version.version}>
          {versionLabel(version.version)} · {formatDate(version.createdAt)}
        </option>
      ))}
    </Select>
  );
}

function useVersionBody(promptId: string, version: number) {
  return useQuery({
    queryKey: promptApi.PROMPT_BODY_KEY(promptId, version),
    queryFn: ({ signal }) => promptApi.getPromptBody(promptId, version, signal),
    // A version's text never changes.
    staleTime: Infinity,
  });
}

function summaryOf(detail: PromptDetail, version: number): PromptVersionSummary | undefined {
  return detail.versions.find((candidate) => candidate.version === version);
}

function olderThan(detail: PromptDetail, version: number): number | null {
  return detail.versions.find((candidate) => candidate.version < version)?.version ?? null;
}
