import { DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import type { TalentHub } from "../api/types";
import { percent } from "../lib/figures";
import { DrawerBulletRow, DrawerLink, ReportDrawer } from "./ReportDrawer";
import { StackedBar } from "./StackedBar";

const DEPTH_FILL = ["bg-sky", "bg-amber", "bg-text3", "bg-line", "bg-line-soft"];

/** One hub opened: how much of the map sits there, how senior it is, and who employs it. */
export function HubDrawer({
  hub,
  located,
  projectId,
  onClose,
}: {
  hub: TalentHub | null;
  /** Everyone with a place on file, the share's denominator. */
  located: number;
  projectId: string;
  onClose: () => void;
}) {
  const share = hub ? percent(hub.count, located) : 0;
  return (
    <ReportDrawer
      open={hub !== null}
      onClose={onClose}
      eyebrow="Hub"
      title={hub ? (hub.country ? `${hub.city} · ${hub.country}` : hub.city) : ""}
      subtitle={hub ? `${hub.count} executives · ${share}% of located talent` : ""}
    >
      {hub && (
        <>
          <DrawerSection title="At a glance">
            <div className="grid grid-cols-3 gap-2">
              <DetailTile label="Execs" value={String(hub.count)} />
              <DetailTile label="Share" value={`${share}%`} />
              <DetailTile label="Interested" value={String(hub.interested)} />
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
          <DrawerSection title="Open">
            <DrawerLink to={`/projects/${projectId}/companies/universe`}>Open {hub.city} on the map</DrawerLink>
          </DrawerSection>
        </>
      )}
    </ReportDrawer>
  );
}
