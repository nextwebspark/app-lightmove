import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { formatRelativeTime } from "../../../lib/format";
import { candidateStatusStyle } from "../../candidates/lib/candidateVocabulary";
import type { CompanyCoverage, Researcher, SourcedExecutive, SourcingQuality, StatusCount } from "../api/types";
import { percent } from "../lib/figures";
import { roleLabel, sinceLabel } from "../lib/teamLabels";
import { STATUS_TONES, statusTone } from "../lib/statusTone";
import { DrawerKpi, DrawerKpis, DrawerSection, ReportDrawer, StatusPill } from "./ReportDrawer";

const SPARK_WIDTH = 340;
const SPARK_HEIGHT = 46;
const MAX_SPARK_DAYS = 90;
const MAX_ACTIVITY = 5;

/** One researcher over the range: where their executives stand, how complete, and when they worked. */
export function ResearcherDrawer({
  researcher,
  days,
  onClose,
}: {
  researcher: Researcher | null;
  days: number;
  onClose: () => void;
}) {
  if (!researcher) return null;
  const spark = researcher.daily.slice(-MAX_SPARK_DAYS);
  const activeDays = spark.filter((count) => count > 0).length;

  return (
    <ReportDrawer
      open
      onClose={onClose}
      eyebrow="Researcher performance"
      title={researcher.name}
      subtitle={`${roleLabel(researcher.role)} · ${sinceLabel(researcher.lastAddedAt)}`}
    >
      <DrawerSection>
        <DrawerKpis columns={2}>
          <DrawerKpi value={researcher.executives} label="Executives" />
          <DrawerKpi value={researcher.companies} label="Companies" />
          <DrawerKpi value={`${researcher.sharePct}%`} label="Share" />
          <DrawerKpi value={researcher.perDay.toFixed(1)} label="Per day" />
        </DrawerKpis>
      </DrawerSection>
      {researcher.executives === 0 ? (
        <DrawerSection>
          <p className="text-xs leading-[1.6] text-u-text3">Nobody filed in this range.</p>
        </DrawerSection>
      ) : (
        <>
          <StatusMix label="Where their executives stand" mix={researcher.statusMix} total={researcher.executives} />
          {researcher.quality && <QualityBars quality={researcher.quality} />}
          <DrawerSection>
            <div className="mb-2 flex justify-between">
              <h3 className="text-[10px] font-bold uppercase tracking-[0.08em] text-u-text3">
                {spark.length}-day activity
              </h3>
              <span className="text-[11.5px] font-semibold text-u-direct">
                Active {activeDays} of {spark.length} days
              </span>
            </div>
            <Sparkline counts={spark} label={`Executives filed per day over ${days} days`} />
          </DrawerSection>
          <DrawerSection label="Recently added executives">
            {researcher.recent.map((executive) => (
              <PersonRow
                key={executive.id}
                executive={executive}
                detail={[executive.companyName, executive.seniority].filter(Boolean).join(" · ")}
              />
            ))}
          </DrawerSection>
        </>
      )}
    </ReportDrawer>
  );
}

/** One covered company: who mapped whom there, how complete those people are, and the recent filings. */
export function CoverageCompanyDrawer({ company, onClose }: { company: CompanyCoverage | null; onClose: () => void }) {
  if (!company) return null;
  const subtitle = [
    company.industry,
    company.city ?? company.country,
    company.employees ? `~${formatEmployees(company.employees)} emps` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <ReportDrawer open onClose={onClose} eyebrow="Company coverage" title={company.name} subtitle={subtitle || "—"}>
      <DrawerSection>
        <DrawerKpis columns={2}>
          <DrawerKpi value={company.executives} label="Mapped" />
          <DrawerKpi value={company.contributors} label="Researchers" />
          <DrawerKpi value={company.stage === "shortlisted" ? "Shortlist" : "Universe"} label="Stage" />
          <DrawerKpi value={company.lastAddedAt ? formatRelativeTime(company.lastAddedAt) : "—"} label="Last added" />
        </DrawerKpis>
      </DrawerSection>
      <StatusMix label={`Where they stand (${company.executives} mapped)`} mix={company.statusMix} total={company.executives} />
      <DrawerSection label="Mapped executives">
        {company.mappedExecutives.map((executive) => (
          <PersonRow
            key={executive.id}
            executive={executive}
            detail={[executive.title, executive.addedByName ? `added by ${executive.addedByName}` : null].filter(Boolean).join(" · ")}
          />
        ))}
      </DrawerSection>
      <QualityBars quality={company.quality} />
      <DrawerSection label="Recent sourcing activity">
        <ol className="flex flex-col gap-3">
          {company.mappedExecutives.slice(0, MAX_ACTIVITY).map((executive) => (
            <li key={executive.id} className="flex gap-2.5">
              <span aria-hidden className="mt-[5px] size-2 flex-none rounded-full bg-u-direct" />
              <span className="text-xs leading-[1.5] text-u-text2">
                <b className="font-semibold text-u-text">{executive.addedByName ?? "Someone"}</b> added {executive.name}
                <span className="block text-[10.5px] text-u-text3">{formatRelativeTime(executive.addedAt)}</span>
              </span>
            </li>
          ))}
        </ol>
      </DrawerSection>
    </ReportDrawer>
  );
}

function StatusMix({ label, mix, total }: { label: string; mix: StatusCount[]; total: number }) {
  return (
    <DrawerSection label={label}>
      <div className="flex flex-col gap-[9px]">
        {mix.map((row) => (
          <div key={row.status}>
            <div className="mb-1 flex justify-between text-xs text-u-text2">
              <span>{candidateStatusStyle(row.status).label}</span>
              <span className="font-u-num font-bold text-u-text">
                {row.count} <span className="font-normal text-u-text3">({percent(row.count, total)}%)</span>
              </span>
            </div>
            <div className="h-[9px] w-full overflow-hidden rounded-[4px] bg-u-sunken">
              <div
                className={cn("h-full rounded-[4px]", STATUS_TONES[statusTone(row.status)].swatch)}
                style={{ width: `${percent(row.count, total)}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </DrawerSection>
  );
}

function QualityBars({ quality }: { quality: SourcingQuality }) {
  const rows = [
    { label: "Contact captured", pct: quality.contactPct },
    { label: "Verified contact", pct: quality.verifiedPct },
    { label: "Comp captured", pct: quality.compPct },
  ];
  return (
    <DrawerSection label="Sourcing data quality">
      <div className="flex flex-col gap-[11px]">
        {rows.map((row) => (
          <div key={row.label}>
            <div className="mb-1 flex justify-between text-xs text-u-text2">
              <span>{row.label}</span>
              <span className="font-u-num font-bold text-u-text">{row.pct}%</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-[3px] bg-u-sunken">
              <div className="h-full rounded-[3px] bg-u-direct" style={{ width: `${row.pct}%` }} />
            </div>
          </div>
        ))}
      </div>
    </DrawerSection>
  );
}

function Sparkline({ counts, label }: { counts: number[]; label: string }) {
  const most = Math.max(...counts, 1);
  const step = counts.length > 1 ? SPARK_WIDTH / (counts.length - 1) : 0;
  const points = counts
    .map((count, day) => `${(day * step).toFixed(1)},${(SPARK_HEIGHT - 3 - (count / most) * (SPARK_HEIGHT - 6)).toFixed(1)}`)
    .join(" ");
  return (
    <div className="rounded-[9px] bg-u-sunken px-3 py-2.5">
      <svg width="100%" height={SPARK_HEIGHT} viewBox={`0 0 ${SPARK_WIDTH} ${SPARK_HEIGHT}`} preserveAspectRatio="none" role="img" aria-label={label}>
        <polyline
          points={counts.length > 1 ? points : `0,${SPARK_HEIGHT - 3} ${SPARK_WIDTH},${SPARK_HEIGHT - 3}`}
          fill="none"
          strokeLinejoin="round"
          strokeLinecap="round"
          strokeWidth={2}
          vectorEffect="non-scaling-stroke"
          className="stroke-u-direct"
        />
      </svg>
    </div>
  );
}

function PersonRow({ executive, detail }: { executive: SourcedExecutive; detail: string }) {
  return (
    <div className="flex items-center gap-[11px] border-t border-u-border px-1 py-2.5">
      <Avatar id={executive.id} name={executive.name} size="md" />
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[13px] font-semibold text-u-text">{executive.name}</span>
        {detail && <span className="mt-0.5 block truncate text-[11.5px] text-u-text3">{detail}</span>}
      </span>
      <StatusPill status={executive.status} />
    </div>
  );
}

function formatEmployees(count: number): string {
  return count >= 1000 ? `${Math.round(count / 1000)}k` : String(count);
}
