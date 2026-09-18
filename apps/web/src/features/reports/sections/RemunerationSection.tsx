import { useState } from "react";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import type { Disclosure, ReportRemuneration } from "../api/types";
import { ChartEmpty } from "../components/ChartEmpty";
import { CompensationStrip } from "../components/CompensationStrip";
import { DisclosureDrawer } from "../components/DisclosureDrawer";
import { KpiTile, KpiTileRow } from "../components/KpiTiles";
import { Legend } from "../components/Legend";
import { LockedBenchmarkCard } from "../components/LockedBenchmarkCard";
import { ReportCard } from "../components/ReportCard";
import { Figure, ReportSection } from "../components/ReportSection";
import { ReportSelect } from "../components/ReportSelect";
import {
  ALL_COUNTRIES,
  ALL_NATIONALITIES,
  type CompensationMeasure,
  compensationStats,
  countriesOf,
  nationalitiesOf,
  nationalityGap,
} from "../lib/compensationStats";
import { formatCompactMoney, ordinal, percent } from "../lib/figures";
import { memberOf, membersOf } from "../lib/nationalityWording";
import { STATUS_TONES } from "../lib/statusTone";

const MEASURE_OPTIONS = [
  { value: "package" as const, label: "Total package" },
  { value: "fixed" as const, label: "Total fixed" },
];

const STATUS_LEGEND = Object.values(STATUS_TONES).map((tone) => ({
  label: tone.label,
  swatchClass: tone.swatch,
  shape: "dot" as const,
}));

/** Are we underpaying, against what the market has actually disclosed rather than an estimate? */
export function RemunerationSection({ remuneration }: { remuneration: ReportRemuneration }) {
  const [measure, setMeasure] = useState<CompensationMeasure>("package");
  const [country, setCountry] = useState(ALL_COUNTRIES);
  const [nationality, setNationality] = useState(ALL_NATIONALITIES);
  const [selected, setSelected] = useState<Disclosure | null>(null);
  const stats = compensationStats(remuneration, { measure, country, nationality });
  const gap = nationalityGap(remuneration);
  const currency = remuneration.currency;
  const money = (amount: number) => formatCompactMoney(currency, amount);
  const measureLabel = measure === "package" ? "total package" : "fixed pay";
  const scopeParts = [
    ...(country !== ALL_COUNTRIES ? [`in ${country}`] : []),
    ...(nationality !== ALL_NATIONALITIES ? [membersOf(nationality)] : []),
  ];
  const scope = scopeParts.length ? ` (${scopeParts.join(", ")})` : "";
  const total = stats.disclosures.length;
  const hasBand = stats.band !== null;

  return (
    <ReportSection
      question="Are we underpaying — against real evidence, not an estimate?"
      findingLabel="At these settings"
      finding={
        stats.band === null ? (
          <>
            The brief states <Figure>no salary band</Figure> yet, so {remuneration.disclosures.length} disclosed packages are
            shown without a benchmark to rank against.
          </>
        ) : stats.isReliable && stats.ceilingPercentile !== null ? (
          <>
            Our offered ceiling (<Figure>{money(stats.band.high)} {measureLabel}</Figure>) sits at the{" "}
            <Figure>{ordinal(stats.ceilingPercentile)} percentile</Figure> of {total} disclosed packages{scope} —{" "}
            {stats.aboveBand} of {total} are priced above it
            {stats.notInterested > 0 ? `, and so were ${stats.notInterestedAboveBand} of ${stats.notInterested} who said no` : ""}.
          </>
        ) : (
          <>
            Only <Figure>{total} disclosed package{total === 1 ? "" : "s"}</Figure>
            {scope} — too few to compute a reliable percentile.
          </>
        )
      }
      lede={
        <>
          Every point below is a <b>named executive</b> whose package is on file — evidence from this mandate's own
          conversations, not a market survey. Click one for the full picture.
        </>
      }
    >
      <KpiTileRow>
        <KpiTile
          tone="lead"
          label="Disclosed packages"
          value={total}
          sub={scope ? scope.replace(/[()]/g, "") : `in ${currency}${remuneration.otherCurrency > 0 ? ` · ${remuneration.otherCurrency} in other currencies not shown` : ""}`}
        />
        <KpiTile label="Median disclosed" value={total ? money(stats.median) : "—"} sub={`${measureLabel} / yr`} />
        <KpiTile
          tone="alarm"
          label="Percentile of our ceiling"
          value={stats.ceilingPercentile === null ? "—" : ordinal(stats.ceilingPercentile)}
          sub={
            !hasBand
              ? "no band in the brief"
              : stats.ceilingPercentile === null
                ? "n too small to compute"
                : stats.ceilingPercentile < 50
                  ? "most of the market pays more"
                  : "competitive against this pool"
          }
        />
        <KpiTile
          tone="alarm"
          label="Priced above our band"
          value={hasBand ? stats.aboveBand : "—"}
          unit={hasBand ? `/${total}` : undefined}
          sub={hasBand ? `${percent(stats.aboveBand, total)}% of disclosed packages` : "no band in the brief"}
        />
      </KpiTileRow>

      <ReportCard
        title="Disclosed compensation"
        caption={`n = ${total} named executives${scope} · click a dot for detail`}
        action={
          <>
            <SegmentedControl variant="uncava" label="Compensation measure" options={MEASURE_OPTIONS} value={measure} onChange={setMeasure} />
            <ReportSelect aria-label="Country" value={country} onChange={(e) => setCountry(e.target.value)}>
              {countriesOf(remuneration).map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </ReportSelect>
            <ReportSelect aria-label="Nationality" value={nationality} onChange={(e) => setNationality(e.target.value)}>
              {nationalitiesOf(remuneration).map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </ReportSelect>
          </>
        }
        note={
          total === 0
            ? "Try “All countries” and “All nationalities” for a larger sample."
            : !hasBand
              ? "State the compensation band in the position brief and this chart will rank the market against it."
              : stats.notInterested > 0
                ? `${stats.notInterestedAboveBand} of ${stats.notInterested} who said no were priced above our top-of-band${scope} — the offered range is likely capping otherwise-strong candidates.`
                : `Nobody in this slice${scope} has said no yet — too few data points to read into.`
        }
      >
        <Legend
          className="mb-1 mt-3"
          items={[...STATUS_LEGEND, { label: "Median", swatchClass: "bg-u-offlimits", shape: "dashed" }]}
        />
        {total > 0 ? (
          <CompensationStrip stats={stats} currency={currency} onSelect={setSelected} />
        ) : (
          <ChartEmpty>No disclosed packages in this slice.</ChartEmpty>
        )}
      </ReportCard>

      <ReportCard
        title="Pay by nationality"
        caption={`total package · all ${remuneration.disclosures.length} disclosures · a fixed comparison, independent of the filters above`}
        note={
          gap && gap.isReliable ? (
            <>
              {gap.higherSide === "rest" ? (
                <>
                  In this pool, <b>non-{membersOf(gap.largestNationality)} command a {gap.gapPct}% premium</b> over{" "}
                  {membersOf(gap.largestNationality)}. Worth confirming at scale before pricing a requirement for{" "}
                  {memberOf(gap.largestNationality)}.
                </>
              ) : (
                <>
                  <b>
                    {membersOf(gap.largestNationality)} command a {gap.gapPct}% premium
                  </b>{" "}
                  over all other nationalities in this pool — worth pricing in explicitly if this mandate specifically
                  needs {memberOf(gap.largestNationality)}.
                </>
              )}{" "}
              Two buckets only: single nationality groups are too thin to compare, and largest-vs-rest is the one split
              both sides clear n ≥ 5 on.
            </>
          ) : (
            "Too few disclosures per nationality group to compare reliably — shown for completeness, not as a finding."
          )
        }
      >
        {gap && gap.isReliable && (
          <KpiTileRow columns={2}>
            <KpiTile
              tone="lead"
              label={`${membersOf(gap.largestNationality)}, median`}
              value={money(gap.largestMedian)}
              sub={`n = ${gap.largestCount} disclosures`}
            />
            <KpiTile
              label="All other nationalities, median"
              value={money(gap.restMedian)}
              sub={`n = ${gap.restCount} disclosures`}
            />
          </KpiTileRow>
        )}
      </ReportCard>

      <LockedBenchmarkCard>
        <b>Cross-mandate compensation benchmark — not built.</b> The percentile
        above ranks our band against the executives <i>this</i> mandate has spoken to, not against the
        market. Aggregating verified packages across mandates is a later piece of work.
      </LockedBenchmarkCard>

      <DisclosureDrawer disclosure={selected} remuneration={remuneration} onClose={() => setSelected(null)} />
    </ReportSection>
  );
}
