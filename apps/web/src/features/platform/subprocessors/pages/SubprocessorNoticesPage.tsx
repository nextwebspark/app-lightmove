import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { PageHeader } from "../../../../components/layout/PageHeader";
import { Button, FormError, LinesSkeleton, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { PromptPill } from "../../prompts/components/PromptPill";
import * as subprocessorApi from "../api/subprocessorApi";
import type { SubprocessorNotice } from "../api/types";
import { PublishSubprocessorNoticeDialog } from "../components/PublishSubprocessorNoticeDialog";
import { admins, effectiveLabel, failedLabel, noticePath, objectionsLabel } from "../lib/subprocessorLabels";

const ROW_GRID =
  "grid min-w-[760px] grid-cols-[minmax(170px,1.3fr)_minmax(170px,1.4fr)_minmax(110px,.8fr)_minmax(130px,.9fr)_minmax(110px,.8fr)] items-center gap-3 px-4";

/** Platform → Sub-processors (`Platform.dc.html`, `subprocessors`): each change announced, and what came back. */
export default function SubprocessorNoticesPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [isPublishing, setPublishing] = useState(false);

  const { data: notices, isPending, isError, error } = useQuery({
    queryKey: subprocessorApi.SUBPROCESSOR_NOTICES_KEY,
    queryFn: ({ signal }) => subprocessorApi.listNotices(signal),
  });

  if (codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;

  return (
    <div>
      <PageHeader
        title="Sub-processor notices"
        subtitle="Workspace admins are emailed 30 days before a new sub-processor starts, and can object."
        action={
          <Button size="sm" onClick={() => setPublishing(true)}>
            <Icon d={ICONS.plus} size={14} />
            Publish a change
          </Button>
        }
      />

      {isPending ? (
        <LinesSkeleton lines={6} />
      ) : isError ? (
        <FormError message={messageFor(error)} />
      ) : (
        <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
          <div role="table" aria-label="Sub-processor notices" className="overflow-x-auto">
            <div role="row" className={cn(ROW_GRID, "border-b border-u-border-strong bg-u-sunken py-2.5")}>
              {["Provider", "What it receives", "Takes effect", "Emailed", "Objections"].map((heading) => (
                <span key={heading} role="columnheader" className="type-micro-label font-mono text-u-text3">
                  {heading}
                </span>
              ))}
            </div>
            {notices.map((notice) => (
              <NoticeRow key={notice.id} notice={notice} onOpen={() => navigate(noticePath(notice.id))} />
            ))}
            {notices.length === 0 && (
              <div className="px-4 py-10 text-center font-mono text-body text-u-text3">
                No sub-processor change has been published.
              </div>
            )}
          </div>
        </div>
      )}

      {isPublishing && (
        <PublishSubprocessorNoticeDialog
          onClose={() => setPublishing(false)}
          onPublished={() => {
            setPublishing(false);
            void queryClient.invalidateQueries({ queryKey: subprocessorApi.SUBPROCESSOR_NOTICES_KEY });
            toast.success("Published. Every workspace admin is being emailed");
          }}
        />
      )}
    </div>
  );
}

function NoticeRow({ notice, onOpen }: { notice: SubprocessorNotice; onOpen: () => void }) {
  return (
    <div
      role="row"
      // A mouse convenience only: the provider's link is the row's keyboard-reachable way in.
      onClick={onOpen}
      className={cn(ROW_GRID, "cursor-pointer border-b border-u-border py-[11px] last:border-b-0 hover:bg-u-sunken")}
    >
      <span role="cell" className="block min-w-0">
        <Link
          to={noticePath(notice.id)}
          // The row would navigate again on the bubbled click, pushing a second history entry.
          onClick={(event) => event.stopPropagation()}
          className="block truncate text-body font-semibold text-u-text hover:underline"
        >
          {notice.provider}
        </Link>
        <span className="mt-0.5 block truncate text-xs text-u-text3">{notice.purpose}</span>
      </span>
      <span role="cell" title={notice.receives} className="truncate text-body text-u-text2">
        {notice.receives}
      </span>
      <span role="cell" className="flex min-w-0 flex-col items-start gap-1">
        <span className="text-body text-u-text2">{effectiveLabel(notice)}</span>
        {notice.inEffect && <PromptPill tone="plain">In effect</PromptPill>}
      </span>
      <span role="cell" className="block min-w-0">
        <span className="block text-body text-u-text2">{admins(notice.emailed)}</span>
        {notice.failed > 0 && <span className="mt-0.5 block font-mono text-xs text-u-offlimits">{failedLabel(notice)}</span>}
      </span>
      <span role="cell" className="block min-w-0">
        {notice.objections > 0 ? (
          <span className="text-body font-medium text-u-accent">{objectionsLabel(notice.objections)}</span>
        ) : (
          <span className="text-body text-u-text3">None</span>
        )}
      </span>
    </div>
  );
}
