import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { countOf } from "../lib/talentMapFeatures";
import type { TreeCompany, TreeExecutive } from "../lib/talentMapTree";

const POPUP_BUTTON =
  "inline-flex items-center gap-1 rounded-[6px] border px-2.5 py-1.5 font-sans text-[12px] font-medium transition";

/**
 * What a clicked pin says: who this is, where, and the one thing to do next. Small on purpose — the
 * detail lives in the drawer, and a popup that tried to be one would cover the map it is anchored to.
 *
 * <p>The <b>name is the way in</b>: it opens the same panel the grid opens, so the popup carries one
 * button rather than two and the thing a reader is already looking at is the thing they click.
 */
export function TalentMapPopup({
  node,
  canWrite,
  onOpen,
  onAddExecutive,
  onClose,
}: {
  node: TreeCompany | TreeExecutive;
  canWrite: boolean;
  onOpen: () => void;
  onAddExecutive?: () => void;
  onClose: () => void;
}) {
  const isCompany = node.kind === "company";
  const title = isCompany ? node.company.companyName : node.candidate.fullName;
  const subtitle = isCompany
    ? [node.company.industry, node.location?.placeLabel ?? node.company.companyCity]
        .filter(Boolean)
        .join(" · ")
    : [node.candidate.title, node.candidate.companyName].filter(Boolean).join(" · ");

  return (
    <div className="w-[260px] rounded-[10px] border border-line bg-panel p-3 font-sans shadow-panel">
      <div className="flex items-start gap-2">
        <span
          className={cn(
            "mt-0.5 flex-none rounded-full px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase tracking-[0.1em]",
            isCompany ? "bg-panel2 text-text2" : "bg-sky-dim text-sky",
          )}
        >
          {isCompany ? "Company" : "Executive"}
        </span>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="ms-auto -mt-1 -me-1 rounded-md p-1 text-text3 transition hover:bg-panel2 hover:text-text"
        >
          <Icon d={ICONS.close} size={13} />
        </button>
      </div>
      <button
        type="button"
        onClick={onOpen}
        className="mt-1.5 block w-full text-start text-[13.5px] font-semibold leading-tight text-text underline-offset-2 transition hover:text-amber hover:underline"
      >
        {title}
      </button>
      {subtitle && <div className="mt-0.5 text-[12px] text-text3">{subtitle}</div>}
      {isCompany && (
        <div className="mt-1 text-[11.5px] text-text3">
          {countOf(node.executives.length, "executive")} mapped
        </div>
      )}
      {isCompany && canWrite && onAddExecutive && (
        <div className="mt-2.5 flex items-center gap-1.5">
          <button
            type="button"
            onClick={onAddExecutive}
            className={cn(POPUP_BUTTON, "border-line bg-panel text-text2 hover:border-text3 hover:text-text")}
          >
            <Icon d={ICONS.userPlus} size={12} />
            Add executive
          </button>
        </div>
      )}
    </div>
  );
}
