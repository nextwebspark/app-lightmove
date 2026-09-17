import type { TalentHub } from "../api/types";
import { formatCompactMoney, percent } from "../lib/figures";
import { DrawerKpi, DrawerKpis, DrawerLink, DrawerListRow, DrawerMeter, DrawerSection, ReportDrawer } from "./ReportDrawer";
import { StackedBar } from "./StackedBar";

const DEPTH_FILL = ["bg-u-accent", "bg-u-border-strong", "bg-u-text3", "bg-u-seq-2", "bg-u-seq-1"];

/** One country opened: how much of the map sits there, who is in it, how senior, and who employs it. */
export function HubDrawer({
  hub,
  located,
  currency,
  highestMedian,
  projectId,
  onClose,
}: {
  hub: TalentHub | null;
  /** Everyone with a place on file, the share's denominator. */
  located: number;
  currency: string;
  /** The best-paid hub's median, which the compensation meter is drawn against. */
  highestMedian: number;
  projectId: string;
  onClose: () => void;
}) {
  const share = hub ? percent(hub.count, located) : 0;
  return (
    <ReportDrawer
      open={hub !== null}
      onClose={onClose}
      eyebrow="Market"
      title={hub?.country ?? ""}
      subtitle={hub ? `${hub.count} executives · ${share}% of located talent` : ""}
    >
      {hub && (
        <>
          <DrawerSection>
            <DrawerKpis columns={2}>
              <DrawerKpi value={hub.count} label="Execs" />
              <DrawerKpi value={`${share}%`} label="Share" />
              <DrawerKpi value={hub.recordedGender > 0 ? `${percent(hub.female, hub.recordedGender)}%` : "—"} label="Female · recorded" />
              <DrawerKpi value={`${percent(hub.interested, hub.count)}%`} label="Interested" />
            </DrawerKpis>
          </DrawerSection>
          <DrawerSection label="Market position">
            <div className="flex flex-col gap-[9px]">
              {hub.medianPackage !== null && highestMedian > 0 && (
                <DrawerMeter
                  label="Median package"
                  pct={(hub.medianPackage / highestMedian) * 100}
                  valueLabel={formatCompactMoney(currency, hub.medianPackage)}
                  fillClass="bg-u-accent"
                />
              )}
              <DrawerMeter
                label="GCC nationals"
                pct={percent(hub.gccNationals, hub.count)}
                valueLabel={`${percent(hub.gccNationals, hub.count)}%`}
                fillClass="bg-u-border-strong"
              />
            </div>
          </DrawerSection>
          <DrawerSection label="Talent depth by level">
            <StackedBar
              segments={hub.depth.map((d, i) => ({ label: d.level, count: d.count, fillClass: DEPTH_FILL[i % DEPTH_FILL.length] }))}
            />
          </DrawerSection>
          {hub.employers.length > 0 && (
            <DrawerSection label="Top employers mapped here">
              {hub.employers.map((c) => (
                <DrawerListRow key={c}>{c}</DrawerListRow>
              ))}
            </DrawerSection>
          )}
          <DrawerLink to={`/projects/${projectId}/companies/universe`}>Open {hub.country} on the map</DrawerLink>
        </>
      )}
    </ReportDrawer>
  );
}
