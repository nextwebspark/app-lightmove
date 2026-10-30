import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { cn } from "../../../lib/cn";
import { hiddenBesideRail } from "../../../lib/useFullscreen";
import type { CompaniesView } from "../../talentmap/lib/useTalentMapPreferences";
import { COMPANIES_VIEW_LABELS } from "../lib/useCompaniesViews";
import { AdvancedMenu, type AdvancedMenuSection } from "./AdvancedMenu";

const TOOLBAR_BUTTON =
  "inline-flex items-center gap-1.5 whitespace-nowrap rounded-md border border-u-border-strong bg-u-surface " +
  "px-3 py-2 font-sans text-body font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text " +
  "aria-disabled:cursor-not-allowed aria-disabled:opacity-50 aria-disabled:hover:border-u-border-strong aria-disabled:hover:text-u-text2";


/** A primary action the current view cannot take: still drawn where it always is, greyed, saying where it can. */
export interface UnavailableAction {
  reason: string;
}

/**
 * The Companies stage toolbar (`Position.dc.html`): one search box whatever the view, the Table | Map |
 * Pipeline control where more than one view is offered, the three primary actions — which keep their
 * places in every view, greyed where the view cannot take them — and Advanced ▾ for everything else.
 *
 * <p>"Add executive" maps someone with no company selected, so the row lands unmapped: the executive a
 * researcher met at a company this mandate never triaged. Adding someone <i>at</i> a company is that
 * company's own row action.
 */
export function TriageToolbar({
  search,
  onSearch,
  searchPlaceholder,
  views,
  view,
  onViewChange,
  isFullscreen,
  canWrite,
  findExecutives,
  onAddExecutive,
  onAddCompany,
  addCompanyUnavailable,
  advanced,
  advancedDot,
}: {
  search: string;
  onSearch: (search: string) => void;
  searchPlaceholder: string;
  /** The views this caller may read the stage as; the control is drawn only for more than one. */
  views: readonly CompaniesView[];
  view: CompaniesView;
  onViewChange: (view: CompaniesView) => void;
  /** Full screen covers the rail, so the toolbar's own view switch stands in for it. */
  isFullscreen: boolean;
  /** False for a client representative, who reads these grids and writes nothing. */
  canWrite: boolean;
  /** Find executives over the universe's companies; takes no second press while a run is in progress. */
  findExecutives?: {
    onPress: () => void;
    running: boolean;
    /** "3/5" while a run is in progress. */
    progress?: string;
    unavailable?: UnavailableAction;
  };
  onAddExecutive: () => void;
  onAddCompany: () => void;
  addCompanyUnavailable?: UnavailableAction;
  advanced: AdvancedMenuSection[];
  /** Says a setting inside Advanced is on, such as hidden columns. */
  advancedDot?: string;
}) {
  return (
    <div className="flex min-h-[52px] flex-none flex-wrap items-center gap-x-3 gap-y-2 border-b border-u-border bg-u-raised px-3 py-2 sm:px-5">
      <label className="flex min-w-[200px] flex-[1_1_260px] items-center gap-2 rounded-md border border-u-border-strong bg-u-surface py-[7px] pl-3 pr-2.5 sm:max-w-[380px]">
        <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
        <input
          type="search"
          value={search}
          onChange={(event) => onSearch(event.target.value)}
          placeholder={searchPlaceholder}
          aria-label={searchPlaceholder}
          className="w-full min-w-0 bg-transparent font-sans text-body text-u-text outline-none placeholder:text-u-text3 [&::-webkit-search-cancel-button]:hidden"
        />
        {search && (
          <button
            type="button"
            onClick={() => onSearch("")}
            aria-label="Clear search"
            className="grid size-5 flex-none place-items-center rounded text-u-text3 hover:bg-u-raised hover:text-u-text"
          >
            <Icon d={ICONS.close} size={12} />
          </button>
        )}
      </label>

      {views.length > 1 && (
        <SegmentedControl
          label="View"
          options={views.map((option) => ({
            value: option,
            label: COMPANIES_VIEW_LABELS[option].label,
            icon: <Icon d={COMPANIES_VIEW_LABELS[option].icon} size={13} />,
          }))}
          value={view}
          onChange={onViewChange}
          className={hiddenBesideRail(isFullscreen)}
        />
      )}

      <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
        {canWrite && (
          <>
            {findExecutives && (
              <ToolbarAction
                icon={ICONS.sparkle}
                pulsing={findExecutives.running}
                unavailable={findExecutives.unavailable}
                busy={findExecutives.running}
                title={
                  findExecutives.running
                    ? "A run is in progress"
                    : "Find executives at the ticked companies, or at the first ones with nobody mapped"
                }
                onClick={findExecutives.onPress}
              >
                {findExecutives.running
                  ? `Finding executives… ${findExecutives.progress ?? ""}`.trimEnd()
                  : "Find executives"}
              </ToolbarAction>
            )}
            <ToolbarAction icon={ICONS.userPlus} onClick={onAddExecutive}>
              Add executive
            </ToolbarAction>
            <ToolbarAction icon={ICONS.plus} unavailable={addCompanyUnavailable} onClick={onAddCompany}>
              Add company
            </ToolbarAction>
          </>
        )}
        <AdvancedMenu sections={advanced} dot={advancedDot} />
      </div>
    </div>
  );
}

function ToolbarAction({
  icon,
  unavailable,
  busy = false,
  pulsing = false,
  title,
  onClick,
  children,
}: {
  icon: string;
  unavailable?: UnavailableAction;
  busy?: boolean;
  pulsing?: boolean;
  title?: string;
  onClick: () => void;
  children: ReactNode;
}) {
  const inert = Boolean(unavailable) || busy;
  return (
    <button
      type="button"
      // aria-disabled rather than disabled, so the greyed button still takes focus and its title is read.
      aria-disabled={inert || undefined}
      aria-busy={busy || undefined}
      title={unavailable?.reason ?? title}
      onClick={inert ? undefined : onClick}
      className={TOOLBAR_BUTTON}
    >
      <Icon d={icon} size={14} className={cn("flex-none", pulsing && "animate-pulse")} />
      {children}
    </button>
  );
}
