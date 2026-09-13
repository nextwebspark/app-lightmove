import { DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import type { Hub } from "../api/types";
import { percent } from "../lib/figures";
import { DrawerBulletRow, DrawerLink, DrawerMeter, DrawerNote, ReportDrawer } from "./ReportDrawer";
import { StackedBar } from "./StackedBar";

const DEPTH_FILL = ["bg-sky", "bg-amber", "bg-text3"];

/** One hub opened: how much of the map sits there, how it pays, and what its labour rules mean. */
export function HubDrawer({
  hub,
  hubTotal,
  projectId,
  onClose,
}: {
  hub: Hub | null;
  hubTotal: number;
  projectId: string;
  onClose: () => void;
}) {
  const share = hub ? percent(hub.count, hubTotal) : 0;
  return (
    <ReportDrawer
      open={hub !== null}
      onClose={onClose}
      eyebrow="Market"
      title={hub ? `${hub.city} · ${hub.country}` : ""}
      subtitle={hub ? `${hub.count} executives · ${share}% of mapped talent` : ""}
    >
      {hub && (
        <>
          <DrawerSection title="At a glance">
            <div className="grid grid-cols-4 gap-2">
              <DetailTile label="Execs" value={String(hub.count)} />
              <DetailTile label="Share" value={`${share}%`} />
              <DetailTile label="Female" value={`${hub.femalePct}%`} />
              <DetailTile label="Open to move" value={`${hub.openToMovePct}%`} />
            </div>
          </DrawerSection>
          <DrawerSection title="Market position">
            <div className="flex flex-col gap-[9px]">
              <DrawerMeter label="Compensation" pct={hub.compensationPct} valueLabel={hub.compensationLabel} fillClass="bg-sky" />
              <DrawerMeter label="Local nationals" pct={hub.nationalsPct} valueLabel={`${hub.nationalsPct}%`} fillClass="bg-amber" />
            </div>
          </DrawerSection>
          <DrawerSection title="Talent depth by level">
            <StackedBar
              height="h-3.5"
              segments={hub.depth.map((d, i) => ({ label: d.level, count: d.count, fillClass: DEPTH_FILL[i % DEPTH_FILL.length] }))}
            />
          </DrawerSection>
          {hub.employers.length > 0 && (
            <DrawerSection title="Top employers mapped here">
              {hub.employers.map((c) => (
                <DrawerBulletRow key={c}>{c}</DrawerBulletRow>
              ))}
            </DrawerSection>
          )}
          <DrawerSection title="Market note">
            <DrawerNote label="Local rules">{hub.note}</DrawerNote>
            <DrawerLink to={`/projects/${projectId}/companies/universe`}>Open {hub.city} on the map</DrawerLink>
          </DrawerSection>
        </>
      )}
    </ReportDrawer>
  );
}
