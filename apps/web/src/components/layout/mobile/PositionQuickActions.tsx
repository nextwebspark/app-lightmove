import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import * as pipelineApi from "../../../features/pipeline/api/pipelineApi";
import { cn } from "../../../lib/cn";
import { BottomSheet, BottomSheetAction } from "../../ui/BottomSheet";
import { Icon, ICONS } from "../Icon";
import { positionPipelinePath } from "../../../features/pipeline/lib/pipelinePath";

const SEARCH_DEBOUNCE_MS = 250;
/** A position can map hundreds; past this many the sheet asks for a few letters rather than drawing them all. */
const MOST_LISTED = 30;

/** What the picked executive's drawer opens on. */
type PersonAction = "note" | "call" | "status";

const PERSON_ACTION_TITLES: Record<PersonAction, string> = {
  note: "Write a note about…",
  call: "Book a call with…",
  status: "Change the status of…",
};

/**
 * The bottom bar's + inside a position: the researcher's everyday adds, and the three things done to one
 * executive — each a door into a screen that already does it, never a second copy of it.
 */
export function PositionQuickActions({
  projectId,
  open,
  onClose,
}: {
  projectId: string;
  open: boolean;
  onClose: () => void;
}) {
  const navigate = useNavigate();
  const [picking, setPicking] = useState<PersonAction | null>(null);
  const base = `/projects/${projectId}`;

  const close = () => {
    setPicking(null);
    onClose();
  };

  const goAdd = (what: "company" | "executive") => {
    close();
    navigate(`${base}/companies/universe?add=${what}`);
  };

  const openPerson = (candidateId: string, action: PersonAction) => {
    // Status needs no tab: its picker is in the drawer's header, over every tab.
    const params: Record<string, string> = { candidate: candidateId };
    if (action === "note") params.tab = "notes";
    if (action === "call") params.section = "meetings";
    close();
    navigate(positionPipelinePath(projectId, params));
  };

  return (
    <BottomSheet
      open={open}
      onClose={close}
      label={picking ? PERSON_ACTION_TITLES[picking] : "Quick actions"}
      title={
        picking ? (
          <span className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPicking(null)}
              aria-label="Back to quick actions"
              className="-ml-1 grid size-8 place-items-center rounded-full text-u-text2 hover:bg-u-raised"
            >
              <Icon d={ICONS.arrowLeft} size={16} />
            </button>
            {PERSON_ACTION_TITLES[picking]}
          </span>
        ) : (
          "Quick actions"
        )
      }
    >
      {picking ? (
        <ExecutivePicker projectId={projectId} onPick={(candidateId) => openPerson(candidateId, picking)} />
      ) : (
        <div className="pb-1">
          <BottomSheetAction
            icon={<Icon d={ICONS.building} size={18} />}
            label="Add company"
            hint="From the market, or typed in"
            onClick={() => goAdd("company")}
          />
          <BottomSheetAction
            icon={<Icon d={ICONS.userPlus} size={18} />}
            label="Add executive"
            hint="Map someone to this position"
            onClick={() => goAdd("executive")}
          />
          <div role="separator" className="mx-3 my-1.5 h-px bg-u-border" />
          <BottomSheetAction
            icon={<Icon d={ICONS.pencil} size={18} />}
            label="Write a note"
            hint="After a call, a meeting, an email"
            onClick={() => setPicking("note")}
          />
          <BottomSheetAction
            icon={<Icon d={ICONS.calendar} size={18} />}
            label="Book a call"
            hint="Pick a time on your calendar"
            onClick={() => setPicking("call")}
          />
          <BottomSheetAction
            icon={<Icon d={ICONS.pipeline} size={18} />}
            label="Change status"
            hint="Move someone along the pipeline"
            onClick={() => setPicking("status")}
          />
        </div>
      )}
    </BottomSheet>
  );
}

function ExecutivePicker({ projectId, onPick }: { projectId: string; onPick: (candidateId: string) => void }) {
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  useEffect(() => {
    const timer = window.setTimeout(() => setQ(search), SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [search]);

  const query = { q, pipelineStage: "" };
  const people = useQuery({
    queryKey: pipelineApi.PIPELINE_KEY(projectId, query),
    queryFn: ({ signal }) => pipelineApi.getPipeline(projectId, query, signal),
    placeholderData: keepPreviousData,
  });
  const rows = people.data?.rows ?? [];
  const listed = rows.slice(0, MOST_LISTED);
  const unlisted = rows.length - listed.length;

  return (
    <div className="flex min-h-[50dvh] flex-col">
      <label className="sticky top-0 z-10 mx-2 mb-2 flex items-center gap-2 rounded-u-chip border border-u-border-strong bg-u-bg px-3">
        <Icon d={ICONS.search} size={15} className="flex-none text-u-text3" />
        <input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Search name, title or company"
          aria-label="Search executives"
          className="h-11 min-w-0 flex-1 bg-transparent text-base text-u-text outline-none placeholder:text-u-text3"
        />
      </label>
      {people.isError ? (
        <p className="px-3 py-6 text-center text-note text-u-text3">We couldn't load this position's executives.</p>
      ) : people.isSuccess && rows.length === 0 ? (
        <p className="px-3 py-6 text-center text-note text-u-text3">
          {q ? "Nobody matches that search." : "Nobody is mapped to this position yet."}
        </p>
      ) : (
        <ul className={cn("pb-1", people.isFetching && "opacity-70")}>
          {listed.map((row) => (
            <li key={row.candidateId}>
              <button
                type="button"
                onClick={() => onPick(row.candidateId)}
                className="flex min-h-12 w-full items-center gap-3 rounded-u-chip px-3 py-2 text-left hover:bg-u-raised focus-visible:bg-u-raised focus-visible:outline-none"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-body font-medium text-u-text">{row.fullName}</span>
                  <span className="block truncate text-meta text-u-text3">
                    {[row.title, row.companyName].filter(Boolean).join(" · ")}
                  </span>
                </span>
                <span className="flex-none rounded-full border border-u-border px-2 py-0.5 text-meta text-u-text2">
                  {row.statusLabel}
                </span>
              </button>
            </li>
          ))}
          {unlisted > 0 && (
            <li className="px-3 py-3 text-center text-meta text-u-text3">
              Keep typing to narrow — {unlisted} more
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
