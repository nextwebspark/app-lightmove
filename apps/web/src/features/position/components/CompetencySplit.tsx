import { cn } from "../../../lib/cn";
import { BriefPanel, FigureInput } from "./BriefFields";

/**
 * How the assessment divides between the two panels. One number is stored — the technical share —
 * and the behavioural card shows the rest, so typing into either keeps the two summing to 100.
 */
export function CompetencySplit({
  technicalShare,
  onChange,
}: {
  technicalShare: number;
  onChange: (technicalShare: number) => void;
}) {
  const behaviouralShare = 100 - technicalShare;

  return (
    <div>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <ShareCard tone="accent" label="Technical" share={technicalShare} onChange={onChange} />
        <ShareCard
          tone="signal"
          label="Behavioural"
          share={behaviouralShare}
          onChange={(share) => onChange(100 - share)}
        />
      </div>
      <div className="mt-3 flex h-1.5 gap-1 overflow-hidden rounded-full" aria-hidden="true">
        <span style={{ width: `${technicalShare}%` }} className="rounded-full bg-u-accent" />
        <span style={{ width: `${behaviouralShare}%` }} className="rounded-full bg-u-signal" />
      </div>
    </div>
  );
}

function ShareCard({
  tone,
  label,
  share,
  onChange,
}: {
  tone: "accent" | "signal";
  label: string;
  share: number;
  onChange: (share: number) => void;
}) {
  return (
    <BriefPanel className="px-5 py-4">
      <span className="flex items-center gap-2 text-[13px] font-medium text-u-text">
        <span className={cn("size-2 rounded-full", tone === "accent" ? "bg-u-accent" : "bg-u-signal")} />
        {label}
      </span>
      <span className="mt-2 flex items-baseline gap-1">
        <FigureInput
          size="lg"
          max={100}
          value={share}
          aria-label={`${label} share`}
          onChange={(value) => onChange(value ?? 0)}
          className="w-[4.5ch] border-transparent focus:border-u-accent"
        />
        <span className="font-u-num text-[22px] text-u-text2">%</span>
      </span>
    </BriefPanel>
  );
}
