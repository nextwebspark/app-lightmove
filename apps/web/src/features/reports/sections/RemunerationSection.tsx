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
import { formatCompactMoney, ordinal, percent } from "../lib/figures";

const MEASURE_OPTIONS = [
  { value: "package" as const, label: "Total package" },
  { value: "fixed" as const, label: "Fixed" },
];

const SELECT_CLASS = "w-auto bg-panel py-[7px] text-[12.5px] font-medium";

/** 03 — are we underpaying, against what the market has actually disclosed rather than an estimate? */
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
    ...(nationality !== ALL_NATIONALITIES ? [`${nationality} nationals`] : []),
  ];
  const scope = scopeParts.length ? ` (${scopeParts.join(", ")})` : "";
  const total = stats.disclosures.length;
  const hasBand = stats.band !== null;

  return (
    <ReportSection
      id="comp"
      ordinal="03"
      eyebrow="Remuneration"
      heading={
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
        stats.isReliable && hasBand
          ? `Every point is a named executive whose package is on file — evidence, not an estimate. ${
              measure === "fixed"
                ? "On fixed pay alone the band may look more competitive — see Total package for the fuller picture."
                : "This is the clearest lever available to widen the pool."
            }`
          : "Points are shown for reference. Widen the filters, or state the band in the position brief, for a read that carries weight."
      }
    >
      <KpiTileRow>
        <KpiTile
          label="Disclosed packages"
          value={total}
          sub={scope ? scope.replace(/[()]/g, "") : `in ${currency}${remuneration.otherCurrency > 0 ? ` · ${remuneration.otherCurrency} in other currencies not shown` : ""}`}
        />
        <KpiTile label="Median disclosed" value={total ? money(stats.median) : "—"} sub={`${measureLabel} / yr`} />
        <KpiTile
          label="Percentile of our ceiling"
          value={stats.ceilingPercentile === null ? "—" : ordinal(stats.ceilingPercentile)}
          valueClass={stats.ceilingPercentile === null ? "text-text3" : stats.ceilingPercentile < 50 ? "text-red" : undefined}
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
          label="Priced above our band"
          value={hasBand ? stats.aboveBand : "—"}
          unit={hasBand ? `/ ${total}` : undefined}
          valueClass={stats.aboveBand > 0 ? "text-red" : undefined}
          sub={hasBand ? `${percent(stats.aboveBand, total)}% of disclosed packages` : "no band in the brief"}
        />
      </KpiTileRow>

      <ReportCard
        title="Disclosed compensation"
        caption={`n = ${total} named executives${scope} · click a dot for detail`}
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
            : !hasBand
              ? "State the compensation band in the position brief and this chart will rank the market against it."
              : stats.notInterested > 0
                ? `${stats.notInterestedAboveBand} of ${stats.notInterested} who said no were priced above our top-of-band${scope} — the offered range is likely capping otherwise-strong candidates.`
                : `Nobody in this slice${scope} has said no yet — too few data points to read into.`
        }
      >
        <ChartLegend
          items={[
            { label: "Identified", swatchClass: "bg-text3", shape: "dot" },
            { label: "In conversation", swatchClass: "bg-sky", shape: "dot" },
            { label: "Interested", swatchClass: "bg-green", shape: "dot" },
            { label: "Not interested", swatchClass: "bg-line", shape: "dot" },
            { label: "Off-limits", swatchClass: "bg-red", shape: "dot" },
            { label: "Out of scope", swatchClass: "bg-amber", shape: "dot" },
            { label: "Median", swatchClass: "bg-amber", shape: "dashed" },
          ]}
        />
        {total > 0 ? (
          <CompensationStrip stats={stats} currency={currency} onSelect={setSelected} />
        ) : (
          <div className="py-[30px] text-center text-[12.5px] text-text3">No disclosed packages in this slice.</div>
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
                  In this pool, <b>non-{gap.largestNationality} nationals command a {gap.gapPct}% premium</b> over{" "}
                  {gap.largestNationality} nationals. Worth confirming at scale before pricing a {gap.largestNationality}
                  -national requirement.
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
              Two buckets only: single nationality groups are too thin to compare, and largest-vs-rest is the one split
              both sides clear n ≥ 5 on.
            </>
          ) : (
            "Too few disclosures per nationality group to compare reliably — shown for completeness, not as a finding."
          )
        }
      >
        {gap && gap.isReliable && (
          <KpiTileRow className="mt-3.5 [grid-template-columns:1fr_1fr]">
            <KpiTile
              className="bg-panel"
              label={`${gap.largestNationality} nationals · median`}
              value={money(gap.largestMedian)}
              sub={`n = ${gap.largestCount} disclosures`}
            />
            <KpiTile
              className="bg-panel"
              label="All other nationalities · median"
              value={money(gap.restMedian)}
              sub={`n = ${gap.restCount} disclosures`}
            />
          </KpiTileRow>
        )}
      </ReportCard>

      <DisclosureDrawer disclosure={selected} remuneration={remuneration} onClose={() => setSelected(null)} />
    </ReportSection>
  );
}
