import { ICONS } from "../../../components/layout/Icon";
import type { MarketSlice, SeniorityLevel } from "../api/types";
import { sliceInterest } from "../lib/marketStats";
import { DrawerContext, DrawerLink, DrawerListRow, DrawerPersonRow, DrawerSection, ReportDrawer } from "./ReportDrawer";
import { ReportGap } from "./ReportGap";
import { StackedBar } from "./StackedBar";

export interface SliceSelection {
  sector: string;
  level: SeniorityLevel;
  count: number;
  slice: MarketSlice | undefined;
}

/** One cell of the heat matrix opened: who is mapped there, at which companies, and where each stands. */
export function MarketSliceDrawer({
  selection,
  projectId,
  onClose,
}: {
  selection: SliceSelection | null;
  projectId: string;
  onClose: () => void;
}) {
  const sector = selection?.sector ?? "";
  const level = selection?.level ?? "Board";
  const count = selection?.count ?? 0;
  const companies = selection?.slice?.companies ?? [];
  const executives = selection?.slice?.executives ?? [];
  const interest = sliceInterest(executives);

  return (
    <ReportDrawer
      open={selection !== null}
      onClose={onClose}
      eyebrow="Market slice"
      title={`${sector} · ${level}`}
      subtitle={`${count} executive${count === 1 ? "" : "s"} mapped${companies.length ? ` · ${companies.length} companies` : ""}`}
    >
      {count === 0 ? (
        <ReportGap icon={ICONS.searchX} title="No executives identified in this slice yet.">
          <DrawerContext label="Context">
            {sector} · {level} is in scope but has not produced an executive. Worth a targeted pass on{" "}
            {sector.toLowerCase()} companies at this level before assuming the market is genuinely empty here.
          </DrawerContext>
        </ReportGap>
      ) : (
        <>
          {executives.length > 0 && (
            <>
              <DrawerSection label="Interest in this slice">
                <StackedBar
                  segments={[
                    { label: "Interested", count: interest.interested, fillClass: "bg-u-direct" },
                    { label: "Not yet", count: interest.passive, fillClass: "bg-u-border-strong" },
                    { label: "Closed", count: interest.closed, fillClass: "bg-u-offlimits" },
                  ]}
                />
              </DrawerSection>
              <DrawerSection label="Executives in this slice">
                {executives.map((e) => (
                  <DrawerPersonRow key={e.id} name={e.fullName} detail={`${e.company ?? "No employer on file"} · ${level}`} status={e.status} />
                ))}
              </DrawerSection>
            </>
          )}
          {companies.length > 0 && (
            <DrawerSection label="Companies contributing">
              {companies.map((c) => (
                <DrawerListRow key={c}>{c}</DrawerListRow>
              ))}
            </DrawerSection>
          )}
          <DrawerLink to={`/projects/${projectId}/companies/universe`}>Open these {count} executives in the grid</DrawerLink>
        </>
      )}
    </ReportDrawer>
  );
}
