import { DetailGrid, DetailPill, DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import { initials } from "../../../lib/format";
import type { MarketSlice, SeniorityLevel, SliceExecutiveStatus } from "../api/types";
import { percent } from "../lib/figures";
import { sliceInterest } from "../lib/marketStats";
import { DrawerBulletRow, DrawerLink, DrawerNote, ReportDrawer } from "./ReportDrawer";
import { StackedBar } from "./StackedBar";

const STATUS_PILL: Record<SliceExecutiveStatus, { label: string; className: string }> = {
  interested: { label: "Interested", className: "bg-green-dim text-green" },
  passive: { label: "Passive", className: "bg-line-soft text-text2" },
  verified: { label: "Verified", className: "bg-sky-dim text-sky" },
  offlimits: { label: "Off-limits", className: "bg-red-dim text-red" },
};

export interface SliceSelection {
  sector: string;
  level: SeniorityLevel;
  count: number;
  slice: MarketSlice | undefined;
}

/** One cell of the heat matrix opened: what is mapped there, who, and what is likely still out there. */
export function MarketSliceDrawer({
  selection,
  projectId,
  onClose,
}: {
  selection: SliceSelection | null;
  projectId: string;
  onClose: () => void;
}) {
  const open = selection !== null;
  const sector = selection?.sector ?? "";
  const level = selection?.level ?? "Board";
  const count = selection?.count ?? 0;
  const slice = selection?.slice;
  const companies = slice?.companies ?? [];
  const executives = slice?.executives ?? [];
  const interest = sliceInterest(executives);

  return (
    <ReportDrawer
      open={open}
      onClose={onClose}
      eyebrow="Market slice"
      title={`${sector} · ${level}`}
      subtitle={`${count} executive${count === 1 ? "" : "s"} mapped${companies.length ? ` · ${companies.length} companies` : ""}`}
    >
      {count === 0 ? (
        <DrawerSection title="Gap">
          <DrawerNote label="No executives identified in this slice yet">
            {sector} · {level} is in scope but has not produced an executive. Worth a targeted pass on{" "}
            {sector.toLowerCase()} companies at this level before assuming the market is genuinely empty here.
          </DrawerNote>
        </DrawerSection>
      ) : (
        <>
          {slice?.insight && (
            <>
              <DrawerSection title="Coverage of this pocket">
                <div className="flex items-center gap-3">
                  <span className="h-2.5 flex-1 overflow-hidden rounded-[5px] bg-line-soft">
                    <span
                      className="block h-2.5 rounded-[5px] bg-sky"
                      style={{ width: `${Math.min(percent(count, slice.insight.estimatedMarket), 100)}%` }}
                    />
                  </span>
                  <span className="font-mono text-xs font-semibold">
                    {Math.min(percent(count, slice.insight.estimatedMarket), 100)}% mapped
                  </span>
                </div>
                <div className="mt-[7px] font-mono text-[10.5px] text-text3">
                  {count} of ~{slice.insight.estimatedMarket} · ~{Math.max(slice.insight.estimatedMarket - count, 0)} likely still in market
                </div>
              </DrawerSection>
              <DrawerSection title="This pocket">
                <DetailGrid>
                  <DetailTile label="Comp-fit" value={`${slice.insight.compFitPct}%`} />
                  <DetailTile label="Female" value={`${slice.insight.femalePct}%`} />
                  <DetailTile label="GCC nationals" value={`${slice.insight.gccNationalsPct}%`} />
                </DetailGrid>
              </DrawerSection>
            </>
          )}
          {executives.length > 0 && (
            <>
              <DrawerSection title="Interest in this slice">
                <StackedBar
                  height="h-3.5"
                  segments={[
                    { label: "Interested", count: interest.interested, fillClass: "bg-green" },
                    { label: "Passive", count: interest.passive, fillClass: "bg-line" },
                    { label: "Off-limits", count: interest.offLimits, fillClass: "bg-red" },
                  ]}
                />
              </DrawerSection>
              <DrawerSection title="Executives in this slice">
                {executives.map((e) => (
                  <div key={e.name} className="flex items-center gap-[11px] border-b border-line-soft py-2 last:border-b-0">
                    <span className="grid size-7 flex-none place-items-center rounded-full border border-line bg-panel2 font-mono text-[10px] font-semibold text-text2">
                      {initials(e.name)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-[12.5px] font-semibold">{e.name}</span>
                      <span className="mt-px block font-mono text-[10.5px] text-text3">
                        {e.company} · {level}
                      </span>
                    </span>
                    <DetailPill label={STATUS_PILL[e.status].label} className={STATUS_PILL[e.status].className} />
                  </div>
                ))}
              </DrawerSection>
            </>
          )}
          {companies.length > 0 && (
            <DrawerSection title="Companies contributing">
              {companies.map((c) => (
                <DrawerBulletRow key={c}>{c}</DrawerBulletRow>
              ))}
            </DrawerSection>
          )}
          <DrawerSection title="Context">
            <DrawerNote label="Reading">
              {sector} {level} is {count >= 10 ? "a core, well-covered pocket" : "a thinner pocket than the FMCG core"}
              {slice?.insight ? `, comp-competitive for ${slice.insight.compFitPct}% of those mapped.` : "."}
            </DrawerNote>
            <DrawerLink to={`/projects/${projectId}/companies/universe`}>Open these {count} executives in the grid</DrawerLink>
          </DrawerSection>
        </>
      )}
    </ReportDrawer>
  );
}
