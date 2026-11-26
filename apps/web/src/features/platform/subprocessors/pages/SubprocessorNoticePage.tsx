import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { NotFoundPage } from "../../../../app/NotFoundPage";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { FormError, LinesSkeleton } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { formatInstantDate } from "../../../../lib/datetime/format";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { usePlatformPageCrumb } from "../../platformCrumb";
import { PromptPill } from "../../prompts/components/PromptPill";
import * as subprocessorApi from "../api/subprocessorApi";
import type { SubprocessorNotice } from "../api/types";
import {
  effectiveLabel,
  failedLabel,
  publishedLabel,
  SUBPROCESSORS_PATH,
  workspaceAdmins,
} from "../lib/subprocessorLabels";

const OBJECTION_GRID =
  "grid min-w-[700px] grid-cols-[minmax(150px,1fr)_minmax(110px,.8fr)_minmax(220px,2.2fr)_110px] gap-3 px-4";

/** One notice (`Platform.dc.html`, `subprocessorDetail`): what was published, and every workspace's objection. */
export default function SubprocessorNoticePage() {
  const { noticeId = "" } = useParams();
  const notices = useQuery({
    queryKey: subprocessorApi.SUBPROCESSOR_NOTICES_KEY,
    queryFn: ({ signal }) => subprocessorApi.listNotices(signal),
  });
  const notice = notices.data?.find((held) => held.id === noticeId);
  usePlatformPageCrumb(notice?.provider ?? null);

  if (codeOf(notices.error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;
  if (notices.isPending) return <LinesSkeleton lines={6} />;
  if (notices.isError) return <FormError message={messageFor(notices.error)} />;
  if (!notice) return <NotFoundPage />;

  const fields: [string, string][] = [
    ["What it receives", notice.receives],
    ["Purpose", notice.purpose],
    ["Region", notice.region],
    [notice.inEffect ? "In effect since" : "Takes effect", effectiveLabel(notice)],
    ["Published", publishedLabel(notice)],
    ["Emailed", `${workspaceAdmins(notice.emailed)}${notice.failed > 0 ? ` · ${failedLabel(notice)}` : ""}`],
  ];

  return (
    <div>
      <Link
        to={SUBPROCESSORS_PATH}
        className="mb-3.5 inline-flex items-center gap-1.5 text-note font-medium text-u-accent hover:underline"
      >
        <Icon d={ICONS.arrowLeft} size={13} />
        Sub-processor notices
      </Link>
      <div className="mb-[18px] flex flex-wrap items-center gap-2.5">
        <h1 className="text-title font-semibold">{notice.provider}</h1>
        {notice.inEffect && <PromptPill tone="plain">In effect</PromptPill>}
      </div>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-x-5 gap-y-3.5 rounded-lg border border-u-border-strong bg-u-surface px-[18px] py-4">
        {fields.map(([label, value]) => (
          <div key={label} className="min-w-0">
            <span className="type-micro-label block font-mono text-u-text3">{label}</span>
            <span className="block text-body text-u-text">{value}</span>
          </div>
        ))}
        <div className="min-w-0">
          <span className="type-micro-label block font-mono text-u-text3">Link to the published list</span>
          <a
            href={notice.listUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="block break-all font-mono text-note text-u-accent hover:underline"
          >
            {notice.listUrl}
          </a>
        </div>
      </div>
      <NoticeObjections notice={notice} />
    </div>
  );
}

function NoticeObjections({ notice }: { notice: SubprocessorNotice }) {
  const objections = useQuery({
    queryKey: subprocessorApi.SUBPROCESSOR_OBJECTIONS_KEY(notice.id),
    queryFn: ({ signal }) => subprocessorApi.listObjections(notice.id, signal),
  });

  return (
    <section aria-label="Objections">
      <h2 className="mb-2.5 mt-6 text-lead font-semibold">
        Objections · {objections.data?.length ?? notice.objections}
      </h2>
      {objections.isPending ? (
        <LinesSkeleton lines={3} />
      ) : objections.isError ? (
        <FormError message={messageFor(objections.error)} />
      ) : objections.data.length === 0 ? (
        <div className="rounded-lg border border-dashed border-u-border-strong px-4 py-6 text-center font-mono text-note text-u-text3">
          No objections yet.
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
          <div role="table" aria-label="Objections" className="overflow-x-auto">
            <div role="row" className={cn(OBJECTION_GRID, "items-center border-b border-u-border-strong bg-u-sunken py-2.5")}>
              {["Workspace", "Admin", "Reason", "Objected on"].map((heading) => (
                <span key={heading} role="columnheader" className="type-micro-label font-mono text-u-text3">
                  {heading}
                </span>
              ))}
            </div>
            {objections.data.map((objection) => (
              <div
                key={objection.workspaceId}
                role="row"
                className={cn(OBJECTION_GRID, "items-start border-b border-u-border py-[11px] last:border-b-0")}
              >
                <span role="cell" className="text-body text-u-text">{objection.workspaceName}</span>
                <span role="cell" className="text-body text-u-text2">{objection.objectedBy ?? "Former member"}</span>
                <span role="cell" className="whitespace-pre-line text-body text-u-text2">{objection.reason}</span>
                <span role="cell" className="font-mono text-note text-u-text2">
                  {formatInstantDate(objection.objectedAt)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
