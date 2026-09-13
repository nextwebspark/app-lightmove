import { DetailPill, DrawerSection } from "../../../components/ui/DetailList";
import { initials } from "../../../lib/format";
import { candidateStatusStyle } from "../../candidates/lib/candidateVocabulary";
import type { MarketSlice, SeniorityLevel } from "../api/types";
import { sliceInterest } from "../lib/marketStats";
import { DrawerBulletRow, DrawerLink, DrawerNote, ReportDrawer } from "./ReportDrawer";
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
        <DrawerSection title="Gap">
          <DrawerNote label="No executives identified in this slice yet">
            {sector} · {level} is in scope but has not produced an executive. Worth a targeted pass on{" "}
            {sector.toLowerCase()} companies at this level before assuming the market is genuinely empty here.
          </DrawerNote>
        </DrawerSection>
      ) : (
        <>
          {executives.length > 0 && (
            <>
              <DrawerSection title="Where the mandate has got to">
                <StackedBar
                  height="h-3.5"
                  segments={[
                    { label: "Interested", count: interest.interested, fillClass: "bg-green" },
                    { label: "Not yet", count: interest.passive, fillClass: "bg-line" },
                    { label: "Closed", count: interest.closed, fillClass: "bg-red" },
                  ]}
                />
              </DrawerSection>
              <DrawerSection title="Executives in this slice">
                {executives.map((e) => (
                  <div key={e.id} className="flex items-center gap-[11px] border-b border-line-soft py-2 last:border-b-0">
                    <span className="grid size-7 flex-none place-items-center rounded-full border border-line bg-panel2 font-mono text-[10px] font-semibold text-text2">
                      {initials(e.fullName)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-[12.5px] font-semibold">{e.fullName}</span>
                      <span className="mt-px block font-mono text-[10.5px] text-text3">
                        {e.company ?? "No employer on file"} · {level}
                      </span>
                    </span>
                    <DetailPill label={candidateStatusStyle(e.status).label} className={candidateStatusStyle(e.status).className} />
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
          <DrawerSection title="Open">
            <DrawerLink to={`/projects/${projectId}/companies/universe`}>Open these {count} executives in the grid</DrawerLink>
          </DrawerSection>
        </>
      )}
    </ReportDrawer>
  );
}
