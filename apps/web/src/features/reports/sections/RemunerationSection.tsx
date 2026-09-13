import { useState } from "react";
import { Select } from "../../../components/ui";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import type { Disclosure, ReportRemuneration } from "../api/types";
import { CompensationStrip } from "../components/CompensationStrip";
import { DisclosureDrawer } from "../components/DisclosureDrawer";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { ChartLegend, ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import {
  ALL_COUNTRIES,
  ALL_NATIONALITIES,
  type CompensationMeasure,
  compensationStats,
  countriesOf,
  nationalitiesOf,
  nationalityGap,
} from "../lib/compensationStats";
import { formatMoneyK, ordinal, percent } from "../lib/figures";

const MEASURE_OPTIONS = [
  { value: "package" as const, label: "Total package" },
  { value: "fixed" as const, label: "Total fixed" },
];

const SELECT_CLASS = "w-auto bg-panel py-[7px] text-[12.5px] font-medium";

/** 03 — are we underpaying, against real evidence rather than an estimate? */
export function RemunerationSection({ remuneration }: { remuneration: ReportRemuneration }) {
  const [measure, setMeasure] = useState<CompensationMeasure>("package");
  const [country, setCountry] = useState(ALL_COUNTRIES);
  const [nationality, setNationality] = useState(ALL_NATIONALITIES);
  const [selected, setSelected] = useState<Disclosure | null>(null);
  const stats = compensationStats(remuneration, { measure, country, nationality });
  const gap = nationalityGap(remuneration);
  const measureLabel = measure === "package" ? "total package" : "total fixed";
  const unit = measure === "package" ? "total comp / yr" : "base salary / yr";
  const scopeParts = [
    ...(country !== ALL_COUNTRIES ? [`in ${country}`] : []),
    ...(nationality !== ALL_NATIONALITIES ? [`${nationality} nationals`] : []),
  ];
  const scope = scopeParts.length ? ` (${scopeParts.join(", ")})` : "";
  const total = stats.disclosures.length;

  return (
    <ReportSection
      id="comp"
      ordinal="03"
      eyebrow="Remuneration"
      heading={
        stats.isReliable && stats.ceilingPercentile !== null ? (
          <>
            Our offered ceiling (<Figure>{formatMoneyK(stats.band.highK)} {measureLabel}</Figure>) sits at the{" "}
            <Figure>{ordinal(stats.ceilingPercentile)} percentile</Figure> of {total} verified disclosures{scope} —{" "}
            {stats.aboveBand} of {total} are priced above it, and so were {stats.declinedAboveBand} of {stats.declined}{" "}
            declines.
          </>
        ) : (
          <>
            Only <Figure>{total} verified disclosure{total === 1 ? "" : "s"}</Figure>
            {scope} — too few to compute a reliable percentile.
          </>
        )
      }
      lede={
        stats.isReliable
          ? `Every point is a named candidate who disclosed compensation during engagement — evidence, not an estimate. ${
              measure === "fixed"
                ? "On base salary alone this band looks far more competitive — see Total package for the fuller picture."
                : "This is the clearest lever available to widen the pool."
            }`
          : "Points are shown for reference only. Widen to “All countries” and “All nationalities” for a statistically meaningful read."
      }
    >
      <KpiTileRow>
        <KpiTile label="Verified disclosures" value={total} sub={scope ? scope.replace(/[()]/g, "") : "candidates who disclosed comp"} />
        <KpiTile label="Median verified" value={total ? formatMoneyK(stats.median) : "—"} sub={unit} />
        <KpiTile
          label="Percentile of our ceiling"
          value={stats.ceilingPercentile === null ? "—" : ordinal(stats.ceilingPercentile)}
          valueClass={stats.ceilingPercentile === null ? "text-text3" : stats.ceilingPercentile < 50 ? "text-red" : undefined}
          sub={
            stats.ceilingPercentile === null
              ? "n too small to compute"
              : stats.ceilingPercentile < 50
                ? "most of the market pays more"
                : "competitive against this pool"
          }
        />
        <KpiTile
          label="Priced above our band"
          value={stats.aboveBand}
          unit={`/ ${total}`}
          valueClass={stats.aboveBand > 0 ? "text-red" : undefined}
          sub={`${percent(stats.aboveBand, total)}% of verified disclosures`}
        />
      </KpiTileRow>

      <ReportCard
        title="Verified compensation"
        caption={`n = ${total} named candidates${scope} · click a dot for detail`}
        action={
          <>
            <SegmentedControl label="Compensation measure" options={MEASURE_OPTIONS} value={measure} onChange={setMeasure} />
            <Select aria-label="Country" value={country} onChange={(e) => setCountry(e.target.value)} className={SELECT_CLASS}>
              {countriesOf(remuneration).map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </Select>
            <Select aria-label="Nationality" value={nationality} onChange={(e) => setNationality(e.target.value)} className={SELECT_CLASS}>
              {nationalitiesOf(remuneration).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </Select>
          </>
        }
        note={
          total === 0
            ? "Try “All countries” and “All nationalities” for a larger sample."
            : stats.declined > 0
              ? `${stats.declinedAboveBand} of ${stats.declined} declines were priced above our top-of-band${scope} — the offered range is likely capping otherwise-strong candidates.`
              : `No declines in this slice${scope} yet — too few data points to read into.`
        }
      >
        <ChartLegend
          items={[
            { label: "Accepted", swatchClass: "bg-green", shape: "dot" },
            { label: "In process", swatchClass: "bg-sky", shape: "dot" },
            { label: "Declined", swatchClass: "bg-red", shape: "dot" },
            { label: "Withdrawn", swatchClass: "bg-text3", shape: "dot" },
            { label: "Median", swatchClass: "bg-amber", shape: "dashed" },
          ]}
        />
        {total > 0 ? (
          <CompensationStrip stats={stats} onSelect={setSelected} />
        ) : (
          <div className="py-[30px] text-center text-[12.5px] text-text3">No verified disclosures in this slice.</div>
        )}
      </ReportCard>

      <ReportCard
        title="Pay by nationality"
        caption={`total package · all ${remuneration.disclosures.length} disclosures · a fixed comparison, independent of the filters above`}
        note={
          gap.isReliable ? (
            <>
              {gap.higherSide === "rest" ? (
                <>
                  In this pool, <b>non-{gap.largestNationality} nationals command a {gap.gapPct}% premium</b> over{" "}
                  {gap.largestNationality} nationals — the opposite of what is often assumed in this market. Worth
                  confirming at scale before pricing a {gap.largestNationality}-national requirement.
                </>
              ) : (
                <>
                  <b>
                    {gap.largestNationality} nationals command a {gap.gapPct}% premium
                  </b>{" "}
                  over all other nationalities in this pool — worth pricing in explicitly if this mandate specifically
                  needs a {gap.largestNationality} national.
                </>
              )}{" "}
              Two buckets only: most single nationality groups here are too thin to say anything reliable, and
              largest-vs-rest is the one split both sides clear n ≥ 5 on.
            </>
          ) : (
            "Too few disclosures per nationality group to compare reliably — shown for completeness, not as a finding."
          )
        }
      >
        {gap.isReliable && (
          <KpiTileRow className="mt-3.5 [grid-template-columns:1fr_1fr]">
            <KpiTile
              className="bg-panel"
              label={`${gap.largestNationality} nationals · median`}
              value={formatMoneyK(gap.largestMedian)}
              sub={`n = ${gap.largestCount} disclosures`}
            />
            <KpiTile
              className="bg-panel"
              label="All other nationalities · median"
              value={formatMoneyK(gap.restMedian)}
              sub={`n = ${gap.restCount} disclosures`}
            />
          </KpiTileRow>
        )}
      </ReportCard>

      <DisclosureDrawer disclosure={selected} remuneration={remuneration} onClose={() => setSelected(null)} />
    </ReportSection>
  );
}
