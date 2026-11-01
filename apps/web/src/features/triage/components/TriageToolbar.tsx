import { Fragment, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import {
  TOOLBAR_OUTLINE,
  TOOLBAR_PRIMARY,
  TOOLBAR_ROW,
  ToolbarDivider,
  ToolbarSearch,
  ToolbarViewSwitch,
} from "../../../components/ui/Toolbar";
import { cn } from "../../../lib/cn";
import type { CompaniesView } from "../../talentmap/lib/useTalentMapPreferences";
import { COMPANIES_VIEW_LABELS } from "../lib/useCompaniesViews";
import { AdvancedMenu, ToolbarMenu, type AdvancedMenuSection } from "./AdvancedMenu";

/** A primary action the current view cannot take: still drawn where it always is, greyed, saying where it can. */
export interface UnavailableAction {
  reason: string;
}

/**
 * The Companies stage toolbar (`Position.dc.html`): the Table | Map | Pipeline switch where more than one view is
 * offered, one search box whatever the view, then Add to sequence and Send email, and — on Table and Map — Find
 * executives, Advanced ▾ and Create ▾. The board needs nothing but the outreach pair: its cards carry their own
 * actions, and companies are added and searched for executives from the grid.
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
  addToSequence,
  sendEmail,
  findExecutives,
  create,
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
  /** The ticked companies' or executives' people, enrolled; absent where outreach is not offered. */
  addToSequence?: { onPress: () => void; unavailable?: UnavailableAction };
  /** Write to anyone on the position; absent where outreach is not offered. */
  sendEmail?: ReactNode;
  /** Find executives over the universe's companies; takes no second press while a run is in progress. */
  findExecutives?: {
    onPress: () => void;
    running: boolean;
    /** "3/5" while a run is in progress. */
    progress?: string;
  };
  /** Create ▾; absent for a client representative, who writes nothing, and on the board. */
  create?: { onAddExecutive: () => void; onAddCompany: () => void };
  /** Advanced ▾; absent on the board. */
  advanced?: AdvancedMenuSection[];
  /** Says a setting inside Advanced is on, such as hidden columns. */
  advancedDot?: string;
}) {
  const groups = [
    {
      key: "outreach",
      node: (addToSequence || sendEmail) && (
        <>
          {addToSequence && (
            <ToolbarAction
              icon={ICONS.outreach}
              unavailable={addToSequence.unavailable}
              onClick={addToSequence.onPress}
              className={TOOLBAR_OUTLINE}
            >
              Add to sequence
            </ToolbarAction>
          )}
          {sendEmail}
        </>
      ),
    },
    {
      key: "grid",
      node: (findExecutives || advanced) && (
        <>
          {findExecutives && (
            <ToolbarAction
              icon={ICONS.search}
              pulsing={findExecutives.running}
              busy={findExecutives.running}
              title={
                findExecutives.running
                  ? "A run is in progress"
                  : "Find executives at the ticked companies, or at the first ones with nobody mapped"
              }
              onClick={findExecutives.onPress}
              className={TOOLBAR_PRIMARY}
            >
              {findExecutives.running
                ? `Finding executives… ${findExecutives.progress ?? ""}`.trimEnd()
                : "Find executives"}
            </ToolbarAction>
          )}
          {advanced && <AdvancedMenu sections={advanced} dot={advancedDot} />}
        </>
      ),
    },
    {
      key: "create",
      node: create && (
        <ToolbarMenu
          label="Create"
          icon={ICONS.plus}
          width={240}
          sections={[
            {
              label: "",
              items: [
                { key: "executive", label: "Add executive", icon: ICONS.userPlus, onSelect: create.onAddExecutive },
                { key: "company", label: "Add company", icon: ICONS.plus, onSelect: create.onAddCompany },
              ],
            },
          ]}
        />
      ),
    },
  ].filter((group) => group.node);
  return (
    <div className={TOOLBAR_ROW}>
      {views.length > 1 && (
        <ToolbarViewSwitch
          label="View"
          views={views.map((option) => ({ value: option, ...COMPANIES_VIEW_LABELS[option] }))}
          value={view}
          onChange={onViewChange}
        />
      )}
      <ToolbarSearch value={search} onChange={onSearch} label={searchPlaceholder} placeholder={searchPlaceholder} />

      <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
        {groups.map((group, index) => (
          <Fragment key={group.key}>
            {index > 0 && <ToolbarDivider />}
            {group.node}
          </Fragment>
        ))}
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
  className,
  children,
}: {
  icon: string;
  unavailable?: UnavailableAction;
  busy?: boolean;
  pulsing?: boolean;
  title?: string;
  onClick: () => void;
  className: string;
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
      className={className}
    >
      <Icon d={icon} size={15} className={cn("flex-none", pulsing && "animate-pulse")} />
      {children}
    </button>
  );
}
